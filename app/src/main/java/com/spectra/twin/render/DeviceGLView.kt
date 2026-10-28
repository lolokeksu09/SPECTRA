package com.spectra.twin.render

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.opengl.GLUtils
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.GestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Approximate Legion Y70 exterior from user reference images, not a CAD scan. */
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
    fun setFinish(value: DeviceFinish) {
        if (scene.finish == value) return
        scene.finish = value
        requestRender()
    }

    private var lastSide: DeviceView? = null
    fun showSide(side: DeviceView) {
        if (lastSide != side) {
            lastSide = side
            scene.yaw = side.yaw
            scene.pitch = side.pitch
            requestRender()
        }
    }

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

/** Immutable CPU-side mesh; generated only when the appearance changes. */
private data class DrawBatch(
    val vertices: FloatBuffer,
    val vertexCount: Int,
    val r: Float, val g: Float, val b: Float
)

private class PhoneRenderer : GLSurfaceView.Renderer {
    @Volatile var finish = DeviceFinish.WHITE
    @Volatile var yaw = 155f
    @Volatile var pitch = -13f
    @Volatile var distance = 4.55f
    private val meshCache = ArrayList<DrawBatch>(48)
    private var cachedFinish: DeviceFinish? = null
    private var program = 0
    private var posHandle = 0
    private var colorHandle = 0
    private var normalHandle = 0
    private var modelViewHandle = 0
    private var decalProgram = 0
    private var decalPos = 0
    private var decalUv = 0
    private var decalMvp = 0
    private var decalSampler = 0
    private var frontTex = 0
    private var whiteTex = 0
    private var blackTex = 0
    private val mv = FloatArray(16)
    private var matrixHandle = 0
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val model = FloatArray(16)
    private val pv = FloatArray(16)
    private val mvp = FloatArray(16)

    override fun onSurfaceCreated(unused: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        GLES20.glClearColor(0.015f, 0.019f, 0.032f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val vertex = """
            uniform mat4 uMvp;
            uniform mat4 uModelView;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            varying vec3 nView;
            varying vec3 vView;
            void main() {
                vec4 p = uModelView * vec4(aPos,1.0);
                nView = normalize((uModelView * vec4(aNormal,0.0)).xyz);
                vView = normalize(-p.xyz);
                gl_Position = uMvp * vec4(aPos,1.0);
            }
        """.trimIndent()
        val fragment = """
            precision mediump float;
            uniform vec4 uColor;
            varying vec3 nView;
            varying vec3 vView;
            void main() {
                vec3 n = normalize(nView);
                vec3 light = normalize(vec3(-0.4,0.7,0.9));
                float d = max(dot(n,light),0.0);
                float s = pow(max(dot(reflect(-light,n),normalize(vView)),0.0),28.0);
                gl_FragColor = vec4(min(vec3(1.0),uColor.rgb*(0.53+0.47*d)+vec3(0.30*s)),1.0);
            }
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
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        modelViewHandle = GLES20.glGetUniformLocation(program, "uModelView")
        decalProgram = makeDecalProgram()
        decalPos = GLES20.glGetAttribLocation(decalProgram, "aPos")
        decalUv = GLES20.glGetAttribLocation(decalProgram, "aUv")
        decalMvp = GLES20.glGetUniformLocation(decalProgram, "uMvp")
        decalSampler = GLES20.glGetUniformLocation(decalProgram, "uTex")
        cachedFinish = null
        meshCache.clear()
        frontTex = uploadBitmap(frontArt())
        whiteTex = uploadBitmap(backArt(true))
        blackTex = uploadBitmap(backArt(false))
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
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelViewHandle, 1, false, mv, 0)

        val appearance = finish
        if (cachedFinish != appearance) {
            meshCache.clear()
            generateGeometry(appearance)
            cachedFinish = appearance
        }
        meshCache.forEach(::renderBatch)

        // The two surface textures are lightweight; all underlying solid geometry
        // is retained between gestures and only regenerated for a finish change.
        decal(frontTex,-.589f,-1.281f,.098f,.589f,1.281f,false)
        decal(if(appearance == DeviceFinish.WHITE) whiteTex else blackTex,
              -.605f,-1.31f,-.089f,.605f,1.31f,true)
    }

    private fun generateGeometry(appearance: DeviceFinish) {
        val white = appearance == DeviceFinish.WHITE
        val body = if (white) floatArrayOf(.85f,.88f,.91f) else floatArrayOf(.10f,.11f,.12f)
        val metal = if (white) floatArrayOf(.64f,.69f,.73f) else floatArrayOf(.25f,.27f,.30f)
        val cam = if (white) floatArrayOf(.69f,.72f,.76f) else floatArrayOf(.16f,.18f,.20f)

        // The external silhouette now has a rounded body and a bevelled rail.
        roundFace(0f,0f,.618f,1.318f,.085f,.075f,.11f,.13f,.16f)
        roundFace(0f,0f,.618f,1.318f,-.085f,.075f,body[0],body[1],body[2])
        roundStrip(0f,0f,.618f,1.318f,.085f,.65f,1.35f,.094f,.085f,metal)
        roundStrip(0f,0f,.65f,1.35f,.094f,.65f,1.35f,-.094f,.085f,metal)
        roundStrip(0f,0f,.65f,1.35f,-.094f,.618f,1.318f,-.085f,.075f,metal)

        // Inset OLED glass, slim bezels and front punch-hole.
        roundFace(0f,0f,.595f,1.285f,.097f,.063f,.015f,.021f,.037f)
        circle(0f,1.21f,.100f,.028f,.012f,.020f,.030f)
        circle(0f,1.21f,.101f,.010f,.07f,.16f,.22f)
        quad(-.36f,-.8f,.099f,.36f,-.794f,.099f,.035f,.14f,.22f)

        // Raised 2x2 island. Rear coordinates are mirrored when viewed from behind;
        // positive model X appears on the viewer's left.
        roundFace(.315f,.88f,.275f,.365f,-.116f,.076f,cam[0],cam[1],cam[2])
        roundStrip(.315f,.88f,.275f,.365f,-.116f,.275f,.365f,-.090f,.076f,cam)
        lens(.43f,1.055f,.113f)
        lens(.19f,1.055f,.091f)
        lens(.43f,.736f,.113f)
        circle(.19f,.736f,-.121f,.082f,.22f,.23f,.24f)
        circle(.19f,.736f,-.124f,.054f,.94f,.85f,.67f)

        // Side hardware buttons.
        side(floatArrayOf(.658f,.22f,-.037f,.658f,.46f,-.037f,.658f,.46f,.040f,.658f,.22f,.040f),
            .59f,.62f,.66f)
        side(floatArrayOf(.658f,.57f,-.037f,.658f,.84f,-.037f,.658f,.84f,.040f,.658f,.57f,.040f),
            .59f,.62f,.66f)

    }

    private fun outline(cx:Float,cy:Float,hw:Float,hh:Float,rad:Float):FloatArray {
        val coords = FloatArray(4 * 13 * 2)
        val corners = arrayOf(
            floatArrayOf(cx+hw-rad,cy+hh-rad,0f),
            floatArrayOf(cx-hw+rad,cy+hh-rad,90f),
            floatArrayOf(cx-hw+rad,cy-hh+rad,180f),
            floatArrayOf(cx+hw-rad,cy-hh+rad,270f)
        )
        var index = 0
        for(c in corners) for(j in 0..12) {
            val angle = (c[2] + j*7.5f) * PI / 180.0
            coords[index++] = c[0]+rad*cos(angle).toFloat()
            coords[index++] = c[1]+rad*sin(angle).toFloat()
        }
        return coords
    }

    private fun roundFace(cx:Float,cy:Float,hw:Float,hh:Float,z:Float,rad:Float,r:Float,g:Float,b:Float) {
        val points=outline(cx,cy,hw,hh,rad)
        val verts=FloatArray(points.size/2*9)
        for(i in 0 until points.size/2) {
            val j=(i+1)%(points.size/2)
            val k=i*9
            verts[k]=cx;verts[k+1]=cy;verts[k+2]=z
            // Face towards camera on the front; the back has reversed winding.
            val a=if(z<0)j else i
            val d=if(z<0)i else j
            verts[k+3]=points[a*2];verts[k+4]=points[a*2+1];verts[k+5]=z
            verts[k+6]=points[d*2];verts[k+7]=points[d*2+1];verts[k+8]=z
        }
        draw(verts,r,g,b)
    }

    private fun roundStrip(cx:Float,cy:Float,hw1:Float,hh1:Float,z1:Float,
                           hw2:Float,hh2:Float,z2:Float,radius:Float,color:FloatArray) {
        val a=outline(cx,cy,hw1,hh1,radius)
        val b=outline(cx,cy,hw2,hh2,radius)
        val verts=FloatArray(a.size/2*18)
        for(i in 0 until a.size/2) {
            val j=(i+1)%(a.size/2)
            val k=i*18
            verts[k]=a[2*i];verts[k+1]=a[2*i+1];verts[k+2]=z1
            verts[k+3]=a[2*j];verts[k+4]=a[2*j+1];verts[k+5]=z1
            verts[k+6]=b[2*j];verts[k+7]=b[2*j+1];verts[k+8]=z2
            verts[k+9]=a[2*i];verts[k+10]=a[2*i+1];verts[k+11]=z1
            verts[k+12]=b[2*j];verts[k+13]=b[2*j+1];verts[k+14]=z2
            verts[k+15]=b[2*i];verts[k+16]=b[2*i+1];verts[k+17]=z2
        }
        draw(verts,color[0],color[1],color[2])
    }

    private fun lens(x:Float,y:Float,r:Float) {
        circle(x,y,-.120f,r,.16f,.17f,.18f)
        circle(x,y,-.124f,r*.83f,.035f,.04f,.052f)
        circle(x,y,-.128f,r*.65f,.013f,.035f,.054f)
        circle(x,y,-.131f,r*.41f,.068f,.15f,.19f)
        circle(x-r*.18f,y+r*.20f,-.134f,r*.12f,.25f,.49f,.58f)
    }


    private fun makeDecalProgram():Int {
        val vertex = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() { vUv = aUv; gl_Position = uMvp * vec4(aPos,1.0); }
        """.trimIndent()
        val fragment = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTex;
            void main() { gl_FragColor = texture2D(uTex,vUv); }
        """.trimIndent()
        val v=shader(GLES20.GL_VERTEX_SHADER,vertex)
        val f=shader(GLES20.GL_FRAGMENT_SHADER,fragment)
        val result=GLES20.glCreateProgram()
        GLES20.glAttachShader(result,v)
        GLES20.glAttachShader(result,f)
        GLES20.glLinkProgram(result)
        val ok=IntArray(1)
        GLES20.glGetProgramiv(result,GLES20.GL_LINK_STATUS,ok,0)
        check(ok[0]==GLES20.GL_TRUE) { "SPECTRA decal shader link: " + GLES20.glGetProgramInfoLog(result) }
        GLES20.glDeleteShader(v)
        GLES20.glDeleteShader(f)
        return result
    }

    private fun uploadBitmap(bitmap:Bitmap):Int {
        val ids=IntArray(1)
        GLES20.glGenTextures(1,ids,0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
        bitmap.recycle()
        return ids[0]
    }

    private fun decal(tex:Int,x1:Float,y1:Float,z:Float,x2:Float,y2:Float,back:Boolean) {
        // Flip U for the rear surface: a viewer facing the rear sees mirrored X.
        val u0=if(back) 1f else 0f
        val u1=if(back) 0f else 1f
        val a=floatArrayOf(
            x1,y1,z,u0,1f,  x2,y1,z,u1,1f,  x1,y2,z,u0,0f,
            x1,y2,z,u0,0f,  x2,y1,z,u1,1f,  x2,y2,z,u1,0f)
        val fb=ByteBuffer.allocateDirect(a.size*4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(a);position(0) }
        GLES20.glUseProgram(decalProgram)
        GLES20.glUniformMatrix4fv(decalMvp,1,false,mvp,0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex)
        GLES20.glUniform1i(decalSampler,0)
        GLES20.glDepthMask(false)
        fb.position(0)
        GLES20.glEnableVertexAttribArray(decalPos)
        GLES20.glVertexAttribPointer(decalPos,3,GLES20.GL_FLOAT,false,20,fb)
        fb.position(3)
        GLES20.glEnableVertexAttribArray(decalUv)
        GLES20.glVertexAttribPointer(decalUv,2,GLES20.GL_FLOAT,false,20,fb)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,6)
        GLES20.glDisableVertexAttribArray(decalUv)
        GLES20.glDisableVertexAttribArray(decalPos)
        GLES20.glDepthMask(true)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(matrixHandle,1,false,mvp,0)
        GLES20.glUniformMatrix4fv(modelViewHandle,1,false,mv,0)
    }

    private fun backArt(white:Boolean):Bitmap {
        val image=Bitmap.createBitmap(512,1024,Bitmap.Config.ARGB_8888)
        val c=Canvas(image)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=if(white) Color.argb(95,82,85,92) else Color.argb(115,148,150,152)
        paint.textAlign=Paint.Align.CENTER
        paint.isFakeBoldText=true
        paint.textSize=95f
        c.save()
        c.translate(256f,552f)
        c.rotate(-90f)
        c.drawText("LEGION",0f,0f,paint)
        c.restore()
        paint.isFakeBoldText=false
        paint.textSize=25f
        c.drawText("Lenovo",256f,913f,paint)
        return image
    }

    private fun frontArt():Bitmap {
        val image=Bitmap.createBitmap(512,1024,Bitmap.Config.ARGB_8888)
        val c=Canvas(image)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=LinearGradient(0f,0f,512f,1024f,
            intArrayOf(Color.rgb(3,9,22),Color.rgb(7,20,39),Color.rgb(3,5,14)),
            null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,512f,1024f,p)
        p.shader=null
        p.strokeWidth=2f
        for(i in 0..13) {
            val y=125f+i*43f
            p.color=Color.argb(32,38,148,210)
            c.drawLine(23f,y,125f+(i%4)*24f,y,p)
            c.drawLine(370f,y+11f,489f,y+11f,p)
        }
        p.textAlign=Paint.Align.CENTER
        p.isFakeBoldText=true
        p.textSize=82f
        p.setShadowLayer(26f,0f,0f,Color.rgb(23,116,227))
        p.color=Color.rgb(133,204,248)
        c.drawText("LEGION",256f,570f,p)
        p.clearShadowLayer()
        p.isFakeBoldText=false
        p.textSize=20f
        p.color=Color.rgb(91,130,153)
        c.drawText("DIGITAL TWIN",256f,609f,p)
        return image
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
        // CPU-side geometry generation is performed once per finish, not every frame.
        val interleaved=FloatArray(vertices.size*2)
        for(i in vertices.indices step 9) {
            val ax=vertices[i];val ay=vertices[i+1];val az=vertices[i+2]
            val bx=vertices[i+3];val by=vertices[i+4];val bz=vertices[i+5]
            val cx=vertices[i+6];val cy=vertices[i+7];val cz=vertices[i+8]
            val ux=bx-ax;val uy=by-ay;val uz=bz-az
            val vx=cx-ax;val vy=cy-ay;val vz=cz-az
            val nx=uy*vz-uz*vy;val ny=uz*vx-ux*vz;val nz=ux*vy-uy*vx
            val length=kotlin.math.sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.000001f)
            for(j in 0..2) {
                val dst=(i/3+j)*6
                val src=i+j*3
                interleaved[dst]=vertices[src]
                interleaved[dst+1]=vertices[src+1]
                interleaved[dst+2]=vertices[src+2]
                interleaved[dst+3]=nx/length
                interleaved[dst+4]=ny/length
                interleaved[dst+5]=nz/length
            }
        }
        val buffer=ByteBuffer.allocateDirect(interleaved.size*4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(interleaved);position(0)
            }
        meshCache.add(DrawBatch(buffer,vertices.size/3,r,g,b))
    }

    private fun renderBatch(batch: DrawBatch) {
        val buffer=batch.vertices
        buffer.position(0)
        GLES20.glUniform4f(colorHandle,batch.r,batch.g,batch.b,1f)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,24,buffer)
        buffer.position(3)
        GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,24,buffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,batch.vertexCount)
        GLES20.glDisableVertexAttribArray(normalHandle)
        GLES20.glDisableVertexAttribArray(posHandle)
    }
}
