package com.spectra.twin.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Native OpenGL ES 2.0 rendering. This is a generic smartphone, not a scan of the Y70. */
@SuppressLint("ViewConstructor")
class DeviceGLView(context: Context) : GLSurfaceView(context) {
    private val scene = PhoneRenderer()
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scene.distance = (scene.distance / detector.scaleFactor).coerceIn(3.2f, 8f)
            requestRender()
            return true
        }
    })
    private var lastX = 0f
    private var lastY = 0f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(scene)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = "Интерактивная трёхмерная модель телефона. Проведите пальцем для вращения. Разведите пальцы для увеличения."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scale.isInProgress && event.pointerCount == 1) {
                    scene.yaw += (event.x - lastX) * 0.45f
                    scene.pitch = (scene.pitch + (event.y - lastY) * 0.35f).coerceIn(-75f, 75f)
                    requestRender()
                }
                lastX = event.x; lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

private class PhoneRenderer : GLSurfaceView.Renderer {
    @Volatile var yaw = -27f
    @Volatile var pitch = -13f
    @Volatile var distance = 4.7f
    private var program = 0
    private var posHandle = 0
    private var colorHandle = 0
    private var matrixHandle = 0
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val model = FloatArray(16)
    private val pv = FloatArray(16)
    private val mvp = FloatArray(16)

    override fun onSurfaceCreated(unused: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        GLES20.glClearColor(0.015f, 0.019f, 0.032f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vertex = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            void main() { gl_Position = uMvp * vec4(aPos, 1.0); }
        """.trimIndent()
        val fragment = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """.trimIndent()
        val vs = shader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = shader(GLES20.GL_FRAGMENT_SHADER, fragment)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "SPECTRA GPU shader link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        posHandle = GLES20.glGetAttribLocation(program, "aPos")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        matrixHandle = GLES20.glGetUniformLocation(program, "uMvp")
    }

    private fun shader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val success = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, success, 0)
        check(success[0] == GLES20.GL_TRUE) { "SPECTRA shader failed: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 40f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 30f)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.setLookAtM(view, 0, 0f, 0f, distance, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.multiplyMM(pv, 0, projection, 0, view, 0)
        Matrix.multiplyMM(mvp, 0, pv, 0, model, 0)
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)

        // Body: front, back and side faces (z-positive = display).
        quad(-0.65f, -1.35f, .085f, .65f, 1.35f, .085f, .09f, .12f, .17f)
        quad(-.65f, -1.35f, -.085f, .65f, 1.35f, -.085f, .11f, .16f, .21f)
        side(floatArrayOf(-.65f,-1.35f,.085f, -.65f,1.35f,.085f, -.65f,1.35f,-.085f, -.65f,-1.35f,-.085f), .24f,.30f,.36f)
        side(floatArrayOf(.65f,-1.35f,-.085f, .65f,1.35f,-.085f, .65f,1.35f,.085f, .65f,-1.35f,.085f), .25f,.31f,.38f)
        side(floatArrayOf(-.65f,1.35f,.085f, .65f,1.35f,.085f, .65f,1.35f,-.085f, -.65f,1.35f,-.085f), .31f,.36f,.42f)
        side(floatArrayOf(-.65f,-1.35f,-.085f, .65f,-1.35f,-.085f, .65f,-1.35f,.085f, -.65f,-1.35f,.085f), .10f,.15f,.20f)

        // Front glass: inset inactive OLED and a narrow dynamic blue visual accent.
        quad(-.607f,-1.27f,.090f, .607f,1.27f,.090f, .018f,.029f,.052f)
        quad(-.565f,-.99f,.092f, .565f,-.985f,.092f, .07f,.46f,.72f)
        quad(-.05f,1.21f,.093f, .05f,1.224f,.093f, .14f,.19f,.24f)
        circle(0f,1.15f,.094f,.012f, .10f,.13f,.17f)

        // Back camera bump and three lenses; intentional generic shape.
        quad(-.55f,.60f,-.096f, -.06f,1.22f,-.096f, .16f,.21f,.26f)
        circle(-.4f,1.08f,-.103f,.108f, .045f,.055f,.07f)
        circle(-.4f,1.08f,-.106f,.063f, .11f,.29f,.40f)
        circle(-.4f,.80f,-.103f,.108f, .045f,.055f,.07f)
        circle(-.4f,.80f,-.106f,.063f, .08f,.24f,.33f)
        circle(-.17f,.95f,-.104f,.073f, .055f,.065f,.08f)
    }

    private fun quad(x1:Float,y1:Float,z1:Float,x2:Float,y2:Float,z2:Float,r:Float,g:Float,b:Float) {
        draw(floatArrayOf(x1,y1,z1, x2,y1,z2, x1,y2,z1,
            x1,y2,z1, x2,y1,z2, x2,y2,z2),r,g,b)
    }
    private fun side(p:FloatArray,r:Float,g:Float,b:Float) {
        draw(floatArrayOf(p[0],p[1],p[2], p[3],p[4],p[5],p[6],p[7],p[8],
            p[0],p[1],p[2],p[6],p[7],p[8],p[9],p[10],p[11]),r,g,b)
    }
    private fun circle(x:Float,y:Float,z:Float,radius:Float,r:Float,g:Float,b:Float) {
        val points = FloatArray(24 * 9)
        for (i in 0 until 24) {
            val a = 2.0*PI*i/24.0
            val bAngle = 2.0*PI*(i+1)/24.0
            val offset = i*9
            points[offset] = x; points[offset+1]=y;points[offset+2]=z
            points[offset+3]=x+(radius*cos(a)).toFloat()
            points[offset+4]=y+(radius*sin(a)).toFloat()
            points[offset+5]=z
            points[offset+6]=x+(radius*cos(bAngle)).toFloat()
            points[offset+7]=y+(radius*sin(bAngle)).toFloat()
            points[offset+8]=z
        }
        draw(points,r,g,b)
    }
    private fun draw(vertices:FloatArray,r:Float,g:Float,b:Float) {
        val buffer:FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices);position(0) }
        GLES20.glUniform4f(colorHandle,r,g,b,1f)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,3*4,buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices.size/3)
        GLES20.glDisableVertexAttribArray(posHandle)
    }
}
