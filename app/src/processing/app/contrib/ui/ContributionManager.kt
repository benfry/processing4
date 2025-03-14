package processing.app.contrib.ui

import androidx.compose.animation.Animatable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import processing.app.Platform
import processing.app.loadPreferences
import java.net.URL
import java.util.*
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.io.path.*


fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        contributionsManager()
    }
}

enum class Status {
    VALID,
    BROKEN,
    DEPRECATED
}
enum class Type {
    library,
    mode,
    tool,
    examples,
}

@Serializable
data class Author(
    val name: String,
    val url: String? = null,
)

@Serializable
data class Contribution(
    val id: Int,
    val status: Status,
    val source: String,
    val type: Type,
    val name: String? = null,
    val categories: List<String>? = emptyList(),
    val authors: String? = null,
    val authorList: List<Author>? = emptyList(),
    val url: String? = null,
    val sentence: String? = null,
    val paragraph: String? = null,
    val version: String? = null,
    val prettyVersion: String? = null,
    val minRevision: Int? = null,
    val maxRevision: Int? = null,
    val download: String? = null,
    val isUpdate: Boolean? = null,
    val isInstalled: Boolean? = null,
)

@Serializable
data class Contributions(
    val contributions: List<Contribution>
)

fun openContributionsManager(){
    // open the compose window

    SwingUtilities.invokeLater {
        val frame = JFrame("Contributions Manager")
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.setSize(800, 600)

        val composePanel = ComposePanel()
        composePanel.setContent {
            contributionsManager()
        }

        frame.contentPane.add(composePanel)
        frame.isVisible = true
    }
}

@Composable
fun contributionsManager(){
    var contributions by remember { mutableStateOf(listOf<Contribution>()) }
    var localContributions by remember { mutableStateOf(listOf<Contribution>()) }
    var error by remember { mutableStateOf<Exception?>(null) }

    val preferences = loadPreferences()

    LaunchedEffect(preferences){
        try {
            localContributions = loadContributionProperties(preferences)
                .map { (type, props) ->
                    Contribution(
                        id = 0,
                        status = Status.VALID,
                        source = "local",
                        type = type,
                        name = props.getProperty("name"),
                        authors = props.getProperty("authors"),
                        url = props.getProperty("url"),
                        sentence = props.getProperty("sentence"),
                        paragraph = props.getProperty("paragraph"),
                        version = props.getProperty("version"),
                        prettyVersion = props.getProperty("prettyVersion"),
                        minRevision = props.getProperty("minRevision")?.toIntOrNull(),
                        maxRevision = props.getProperty("maxRevision")?.toIntOrNull(),
                        download = props.getProperty("download"),
                    )
                }
        } catch (e: Exception){
            error = e
        }
    }


    LaunchedEffect(Unit){
        try {
            val url = URL("https://github.com/mingness/processing-contributions-new/raw/refs/heads/main/contributions.yaml")
            val connection = url.openConnection()
            val inputStream = connection.getInputStream()
            val yaml = inputStream.readAllBytes().decodeToString()
            // TODO cache yaml in processing folder

            val parser = Yaml(
                configuration = YamlConfiguration(
                    strictMode = false
                )
            )
            val result = parser.decodeFromString(Contributions.serializer(), yaml)

            contributions = result.contributions
                .filter { it.status == Status.VALID }
                .map {
                    // TODO Parse better
                    val authorList = it.authors?.split(",")?.map { author ->
                        val parts = author.split("](")
                        val name = parts[0].removePrefix("[")
                        val url = parts.getOrNull(1)?.removeSuffix(")")
                        Author(name, url)
                    } ?: emptyList()
                    it.copy(authorList = authorList)
                }
        } catch (e: Exception){
            error = e
        }
    }
    if(error != null){
        Text("Error loading contributions: ${error?.message}")
        return
    }
    if(contributions.isEmpty()){
        Text("Loading contributions...")
        return
    }

    val contributionsByType = (contributions + localContributions)
        .groupBy { it.name }
        .map { (_, contributions) ->
            if(contributions.size == 1) return@map contributions.first()
            else{
                // check if they all have the same version, otherwise return the newest version
                val versions = contributions.mapNotNull { it.version }
                if(versions.toSet().size == 1) return@map contributions.first().copy(isInstalled = true)
                else{
                    val newest = contributions.maxByOrNull { it.version?.toIntOrNull() ?: 0 }
                    if(newest != null) return@map newest.copy(isUpdate = true, isInstalled = true)
                    else return@map contributions.first().copy(isUpdate = true, isInstalled = true)
                }
            }
        }
        .groupBy { it.type }

    val types = Type.entries
    var selectedType by remember { mutableStateOf(types.first()) }
    val contributionsForType = (contributionsByType[selectedType] ?: emptyList())
        .sortedBy { it.name }

    var selectedContribution by remember { mutableStateOf<Contribution?>(null) }
    Box{
        Column {
            Row{
                for(type in types){
                    val background = remember { Animatable(Color.Transparent) }
                    val color = remember { Animatable(Color.Black) }
                    LaunchedEffect(selectedType){
                        if(selectedType == type){
                            background.animateTo(Color(0xff0251c8))
                            color.animateTo(Color.White)
                        }else{
                            background.animateTo(Color.Transparent)
                            color.animateTo(Color.Black)
                        }
                    }

                    Row(modifier = Modifier
                        .background(background.value)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            selectedType = type
                            selectedContribution = null
                        }
                        .padding(16.dp, 8.dp)
                    ){
                        Text(type.name, color = color.value)
                        val updates = contributionsByType[type]?.count { it.isUpdate == true } ?: 0
                        if(updates > 0){
                            Text("($updates)")
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)){
                val state = rememberLazyListState()
                LazyColumn(state = state) {
                    item{
                        // Table Header
                    }
                    items(contributionsForType){ contribution ->
                        Row(modifier = Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { selectedContribution = contribution }
                            .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(modifier = Modifier.weight(1f)){
                                if(contribution.isUpdate == true){
                                    Text("Update")
                                }else if(contribution.isInstalled == true){
                                    Text("Installed")
                                }

                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(8f)){
                                Text(contribution.name ?: "Unnamed", fontWeight = FontWeight.Bold)
                                Text(contribution.sentence ?: "No description", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(modifier = Modifier.weight(4f)){
                                Text(contribution.authorList?.joinToString { it.name } ?: "Unknown")
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .background(Color.LightGray)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(
                        scrollState = state
                    )
                )
            }
            ContributionPane(
                contribution = selectedContribution,
                onClose = { selectedContribution = null }
            )
        }

    }

}


fun loadContributionProperties(preferences: Properties): List<Pair<Type, Properties>>{
    val result = mutableListOf<Pair<Type, Properties>>()
    val sketchBook = Path(preferences.getProperty("sketchbook.path.four", Platform.getDefaultSketchbookFolder().path))
    sketchBook.forEachDirectoryEntry{ contributionsFolder ->
        if(!contributionsFolder.isDirectory()) return@forEachDirectoryEntry
        val typeName = contributionsFolder.fileName.toString()
        val type: Type = when(typeName){
            "libraries" -> Type.library
            "modes" -> Type.mode
            "tools" -> Type.tool
            "examples" -> Type.examples
            else -> return@forEachDirectoryEntry
        }
        contributionsFolder.forEachDirectoryEntry { contribution ->
            if(!contribution.isDirectory()) return@forEachDirectoryEntry
            contribution.forEachDirectoryEntry("*.properties"){ entry ->
                val props = Properties()
                props.load(entry.inputStream())
                result += Pair(type, props)
            }
        }
    }
    return result
}