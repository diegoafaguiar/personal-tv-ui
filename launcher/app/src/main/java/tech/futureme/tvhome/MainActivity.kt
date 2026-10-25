package tech.futureme.tvhome

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import tech.futureme.tvhome.data.InputEntry
import tech.futureme.tvhome.data.MediaItem
import tech.futureme.tvhome.ui.App
import tech.futureme.tvhome.ui.TvHomeTheme

/** Declarada pelo TvProvider, não pelo SDK: por isso a string e não Manifest.permission. */
const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvHomeTheme {
                App(
                    vm = vm,
                    onOpenItem = ::openItem,
                    onOpenUrl = ::openUrl,
                    onOpenMusic = ::openMusic,
                    onOpenApp = { vm.recordRecent(it.packageName, it.label, null); startSafely(it.launchIntent) },
                    onOpenRecent = ::openRecent,
                    onTuneFailed = ::tuneFailed,
                    onOpenInput = ::openInput,
                    onQuick = { startSafely(it.intent()) },
                    onRequestPermission = { askPermission.launch(READ_TV_LISTINGS) },
                )
            }
        }
        if (ContextCompat.checkSelfPermission(this, READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) {
            askPermission.launch(READ_TV_LISTINGS)
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()   // voltou de um app: o Watch Next pode ter mudado
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Botão Home com o launcher aberto (singleTask): o sistema entrega um novo intent HOME
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.homePressed.value++
        vm.refresh()
    }

    /** O botão Input do controle abre a TV ao vivo / menu de fontes mesmo com a home na frente. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            val first = vm.content.value.inputs.firstOrNull()
            if (first != null) { openInput(first); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openRecent(entry: tech.futureme.tvhome.data.RecentEntry) {
        vm.recordRecent(entry.packageName, entry.title, entry.intentUri)
        val uri = entry.intentUri
        if (uri.isNullOrBlank()) { openApp(entry.packageName); return }
        try { startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { openApp(entry.packageName) }
    }

    /** O TvView não conseguiu: tenta o app de TV ao vivo da TV pelo canal, senão abre o app. */
    private fun tuneFailed(ch: tech.futureme.tvhome.data.TunerChannel, why: String) {
        Log.w("TvHome", "Sintonia falhou para ${ch.number} ${ch.name}: $why")
        Toast.makeText(this, "$why. Abrindo o app de TV.", Toast.LENGTH_LONG).show()
        val live = vm.settings.value.liveTvPackage
        // O app da TCL declara VIEW para vnd.android.cursor.item/channel: pacote e tipo explícitos para resolver
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(android.media.tv.TvContract.buildChannelUri(ch.id), "vnd.android.cursor.item/channel").setPackage(live)
        val tuner = vm.content.value.inputs.firstOrNull { it.isTuner }
        when {
            view.resolveActivity(packageManager) != null -> startSafely(view)
            tuner != null -> openInput(tuner)
            else -> openApp(live)
        }
    }

    private fun openItem(item: MediaItem) {
        vm.recordRecent(item.packageName, item.title, item.intentUri)
        val uri = item.intentUri
        if (uri.isNullOrBlank()) { openApp(item.packageName); return }
        try {
            val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("TvHome", "intent_uri inválido para ${item.title}: $uri", e)
            openApp(item.packageName)
        }
    }

    /** Abre uma URL em quem souber (navegador, app do YouTube). Devolve false se ninguém atende. */
    private fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = intent.resolveActivity(packageManager) ?: return false
        // Android TV sem navegador resolve para um "stub" que não mostra nada: tratar como sem navegador
        if (target.packageName == "com.android.tv.frameworkpackagestubs") return false
        return try { startActivity(intent); true } catch (e: Exception) { false }
    }

    /** Sugestão musical: busca no Spotify, senão no YouTube, senão abre o primeiro app de música. */
    private fun openMusic(query: String) {
        val spotify = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("spotify:search:" + android.net.Uri.encode(query))).setPackage("com.spotify.tv.android")
        if (spotify.resolveActivity(packageManager) != null) { startSafely(spotify); return }
        val yt = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode(query))).setPackage("com.google.android.youtube.tv")
        if (yt.resolveActivity(packageManager) != null) { startSafely(yt); return }
        val any = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=" + android.net.Uri.encode(query)))
        if (any.resolveActivity(packageManager) != null) { startSafely(any); return }
        Toast.makeText(this, "Instale o Spotify ou o YouTube para abrir sugestões", Toast.LENGTH_SHORT).show()
    }

    private fun openApp(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) Toast.makeText(this, "App não encontrado: $packageName", Toast.LENGTH_SHORT).show()
        else startSafely(intent)
    }

    private fun openInput(input: InputEntry) {
        val intent = vm.appsRepo.inputIntent(input, vm.settings.value.liveTvPackage)
        if (intent == null) Toast.makeText(this, "Não achei o app de TV ao vivo", Toast.LENGTH_SHORT).show()
        else startSafely(intent)
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("TvHome", "Não consegui abrir $intent", e)
            Toast.makeText(this, "Não consegui abrir", Toast.LENGTH_SHORT).show()
        }
    }
}
