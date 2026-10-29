#!/usr/bin/env python3
"""Add a non-destructive way to prefer the bundled v9 over an old imported GLB."""
from pathlib import Path

def rewrite(path, old, new, label):
    value=path.read_text()
    if new in value: return
    if value.count(old)!=1: raise SystemExit('Unsupported SPECTRA source ('+label+'): '+str(path))
    path.write_text(value.replace(old,new))

vm=Path('app/src/main/java/com/spectra/twin/ui/SpectraViewModel.kt')
text=vm.read_text()
if 'fun restoreBundledModel(' not in text:
    rewrite(vm,
        '    private val preferences = app.getSharedPreferences("spectra-visual-v1", Context.MODE_PRIVATE)',
        """    /** Non-destructive: clear only an imported GLB URI, never diagnostic snapshots. */
    fun restoreBundledModel(finish: DeviceFinish) {
        when (finish) {
            DeviceFinish.WHITE -> {
                importedModelUri = null
                visualPrefs.edit().remove("white_glb_uri").apply()
            }
            DeviceFinish.BLACK -> {
                blackModelUri = null
                visualPrefs.edit().remove("black_glb_uri").apply()
            }
        }
    }
    private val preferences = app.getSharedPreferences("spectra-visual-v1", Context.MODE_PRIVATE)""",
        'reset state')

ui=Path('app/src/main/java/com/spectra/twin/ui/SpectraApp.kt')
s=ui.read_text()
if 'onRestoreBundled: (DeviceFinish) -> Unit' not in s:
    rewrite(ui,
        '                    model.blackModelUri, model::importBlackModelUri, model::snapshot)',
        """                    model.blackModelUri, model::importBlackModelUri,
                    model::restoreBundledModel, model::snapshot)""",
        'app callback')
    rewrite(ui,
        """                       blackModelUri: String?, onImportBlack: (String) -> Unit,
                       onSnapshot: () -> Unit) {""",
        """                       blackModelUri: String?, onImportBlack: (String) -> Unit,
                       onRestoreBundled: (DeviceFinish) -> Unit,
                       onSnapshot: () -> Unit) {""",
        'screen signature')
    rewrite(ui,
        """        StableTwinItem {
            Caption(
                if (hasBuiltInBlack) """,
        """        // Imported models override bundled assets. This reset is explicit,
        // affects only the chosen finish, and does not erase snapshot history.
        if ((finish == DeviceFinish.WHITE && importedModelUri != null) ||
            (finish == DeviceFinish.BLACK && blackModelUri != null && hasBuiltInBlack)) {
            StableTwinItem {
                OutlinedButton(
                    onClick = { onRestoreBundled(finish) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("Вернуться к встроенной модели v9") }
            }
        }
        StableTwinItem {
            Caption(
                if (hasBuiltInBlack) """,
        'button placement')
s=ui.read_text()
if 'Вернуться к встроенной модели v4' in s:
    ui.write_text(s.replace('Вернуться к встроенной модели v4','Вернуться к встроенной модели v9'))
s=ui.read_text()
if 'Использовать встроенную модель v4' in s:
    ui.write_text(s.replace('Использовать встроенную модель v4','Вернуться к встроенной модели v9'))
s=ui.read_text()
old='Белая и чёрная модели GLB встроены в APK. Без подключения к интернету.'
if old in s:
    ui.write_text(s.replace(old,'Белая и чёрная 3D-модели Legion Y70 v9 встроены в APK и работают офлайн.'))
print('SPECTRA UI: v9 asset selection and optional import reset are ready')
