package fr.augustine.androgustine.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.augustine.androgustine.R
import fr.augustine.androgustine.data.imports.readFileImportSummary
import fr.augustine.androgustine.ui.components.CircuitView
import fr.augustine.androgustine.ui.theme.OxaniumFontFamily
import fr.augustine.androgustine.ui.theme.ShellGrey
import fr.augustine.androgustine.ui.theme.ShellOrange
import fr.augustine.androgustine.ui.theme.FlagGreen
import fr.augustine.androgustine.ui.theme.FlagYellow
import fr.augustine.androgustine.ui.theme.DangerRed
import fr.augustine.androgustine.viewmodel.RaceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PilotScreen(viewModel: RaceViewModel = viewModel()) {

    // Désactivation de la veille de l'écran quand la page est au 1er plan :
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val coroutineScope = rememberCoroutineScope()
    var importSummaryText by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        importSummaryText = "Lecture du fichier..."
        coroutineScope.launch {
            importSummaryText = runCatching {
                withContext(Dispatchers.IO) {
                    readFileImportSummary(contentResolver, uri)
                }
            }.fold(
                onSuccess = { summary ->
                    buildString {
                        appendLine("Nom : ${summary.name ?: "non disponible"}")
                        appendLine("Type MIME : ${summary.mimeType ?: "non disponible"}")
                        appendLine("Taille : ${summary.sizeBytes?.let { "$it octets" } ?: "non disponible"}")
                        appendLine("Caracteres lus : ${summary.characterCount}")
                        appendLine("Apercu 200 caracteres :")
                        append(summary.preview)
                    }
                },
                onFailure = { error ->
                    "Erreur de lecture : ${error.localizedMessage ?: error.message ?: "erreur inconnue"}"
                }
            )
        }
    }

    // Effet qui s'exécute quand l'écran est affiché
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Nettoyage : on retire le flag quand on quitte cet écran
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    // Style de base pour la police :
    val textStyle = androidx.compose.ui.text.TextStyle(
        fontFamily = OxaniumFontFamily,
        color = Color.White
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // Fond d'écran avec tachymètre
        Image(
            painter = painterResource(id = R.drawable.background_landscape),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Grille principale :
        Column(modifier = Modifier.fillMaxSize()) {

            // --- LIGNE 1 (Tours, Circuit, Temps) ---
            Row(modifier = Modifier.weight(0.4f).fillMaxWidth()) {
                // Zone Tours (Haut Gauche)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ico_loop), null, Modifier.size(38.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.lapProgress,
                            style= textStyle.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            )
                    }
                    Spacer(Modifier.height(10.dp))
                    Image(painterResource(R.drawable.ico_stands), null, Modifier.size(60.dp))
                    Text(
                        text = "stands",
                        style = textStyle.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Zone Circuit (Centre)
                Box(modifier = Modifier.weight(1.5f).fillMaxHeight()) {
                    CircuitView(
                        points = uiState.circuitPoints,
                        currentLat = uiState.currentLat,
                        currentLon = uiState.currentLon,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Zone Chrono / Accélérer (Haut Droite)
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    )
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ico_timer), null, Modifier.size(38.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = uiState.timer,
                            style = textStyle.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Image(painterResource(R.drawable.ico_speed_meter), null, Modifier.size(60.dp))
                    Text(
                        text = "accélérer",
                        style = textStyle.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // --- LIGNE 2 (Vitesse Centrale) ---
            Box(modifier = Modifier.weight(0.4f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%.0f".format(uiState.speed),
                        style = textStyle.copy(
                            fontSize = 130.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-2).sp
                        )
                    )
                    Column(Modifier.padding(bottom = 20.dp, start = 8.dp)) {
                        Text(
                            text = "(vit. GPS)",
                            color = ShellGrey,
                            style = textStyle.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal
                            )
                        )
                        Text(
                            text = "km/h",
                            color = ShellOrange,
                            style = textStyle.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // --- LIGNE 3 (Drapeaux / Instructions) ---
            Row(
                modifier = Modifier.weight(0.2f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    //painter = painterResource(id = if (uiState.flag == "jaune") R.drawable.flag_yellow else R.drawable.flag_red),
                    painter = painterResource(id = when (uiState.flag) {
                        "jaune" -> R.drawable.flag_yellow
                        "rouge" -> R.drawable.flag_red
                        else -> R.drawable.ico_on_track
                    }),
                    contentDescription = null,
                    modifier = Modifier.size(50.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = uiState.flag,
                    style = textStyle.copy(
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        color = FlagGreen
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .widthIn(min = 220.dp, max = 420.dp),
            horizontalAlignment = Alignment.End
        ) {
            Button(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "text/*",
                            "text/csv",
                            "application/csv",
                            "application/json",
                            "text/json"
                        )
                    )
                }
            ) {
                Text("Tester import fichier")
            }

            importSummaryText?.let { summaryText ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summaryText,
                    modifier = Modifier
                        .background(Color(0xCC00334D))
                        .padding(10.dp)
                        .heightIn(max = 170.dp)
                        .verticalScroll(rememberScrollState()),
                    style = textStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}
