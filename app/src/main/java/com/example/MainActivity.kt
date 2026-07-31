package com.example

import android.os.Bundle
import android.widget.Toast
import com.example.data.SggsDatabase
import com.example.data.LineEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.example.db.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshotFlow
import android.content.Intent
private var cachedPunjabiTranslationMap: Map<String, String>? = null
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val database = AppDatabase.getDatabase(this)
    val repository = BookmarkRepository(database.bookmarkDao())
    val viewModelFactory = BookmarksViewModelFactory(repository)
    val viewModel = androidx.lifecycle.ViewModelProvider(this, viewModelFactory)[BookmarksViewModel::class.java]
    val settingsManager = SettingsManager(applicationContext)

    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
      try {
        val sggsDb = com.example.data.SggsDatabase.getInstance(applicationContext)
        sggsDb.getReadableDb()

        val nitnemFiles = listOf(
          "japji_sahib.json",
          "jaap_sahib.json",
          "tav_prasad_savaiye.json",
          "chaupai_sahib.json",
          "anand_sahib.json",
          "rehras_sahib.json",
          "kirtan_sohila.json",
          "ardas.json",
          "aarti.json",
          "asa_di_vaar.json",
          "sri_sukhmani_sahib.json"
        )
        for (file in nitnemFiles) {
          try {
            if (sggsDb.getCachedNitnemBani(file) == null) {
              loadBaniFromAsset(applicationContext, file)
            }
          } catch (_: Exception) {}
        }
      } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error in background pre-warm: ${e.message}")
      }
    }
    setContent {
      val settingsState by settingsManager.settings.collectAsStateWithLifecycle()
      val isDark = when (settingsState.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
      }

      val view = androidx.compose.ui.platform.LocalView.current
      LaunchedEffect(settingsState.keepScreenOn) {
        view.keepScreenOn = settingsState.keepScreenOn
      }

      MyApplicationTheme(darkTheme = isDark) {
        MainApp(viewModel, settingsManager)
      }
    }
  }
}

// Gurbani Offline Data Models
data class Bani(
  val fileName: String,
  val title: String,
  val verses: List<Verse>
)

data class Verse(
  val id: Int,
  val index: Int,
  val line: String,
  val pauseType: String? = null,
  val bookmarked: Boolean = false,
  val translation: String = "",
  val punjabiTranslation: String = ""
)

enum class SearchFilterType {
  ALL,          // ਸਭ
  FIRST_LETTER, // ਪਹਿਲਾ ਅੱਖਰ
  FULL_TEXT,    // ਪੂਰਾ ਪਾਠ
  ANG           // ਅੰਗ ਨੰਬਰ
}

data class SearchResult(
  val baniName: String,
  val fileName: String,
  val verse: Verse,
  val searchMethod: SearchFilterType = SearchFilterType.FULL_TEXT,
  val highlightRange: IntRange? = null,
  val matchedQuery: String = "",
  val ang: Int = 1
)

fun getBaniFileName(title: String): String {
  val t = title.trim()
  if (t.endsWith(".json")) return t
  val lower = t.lowercase()
  return when {
    t == "ਜਪੁਜੀ ਸਾਹਿਬ" || lower.contains("japji") || lower.contains("ਜਪੁਜੀ") -> "japji_sahib.json"
    t == "ਜਾਪੁ ਸਾਹਿਬ" || lower.contains("jaap") || lower.contains("ਜਾਪੁ") -> "jaap_sahib.json"
    t == "ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ" || lower.contains("tav_prasad") || lower.contains("savaiye") || lower.contains("ਸਵੱਯੇ") -> "tav_prasad_savaiye.json"
    t == "ਚੌਪਈ ਸਾਹਿਬ" || lower.contains("chaupai") || lower.contains("ਚੌਪਈ") -> "chaupai_sahib.json"
    t == "ਅਨੰਦ ਸਾਹਿਬ" || lower.contains("anand") || lower.contains("ਅਨੰਦ") -> "anand_sahib.json"
    t == "ਰਹਿਰਾਸ ਸਾਹਿਬ" || lower.contains("rehras") || lower.contains("rehraas") || lower.contains("ਰਹਿਰਾਸ") || lower.contains("ਰਹਰਾਸ") -> "rehras_sahib.json"
    t == "ਅਰਦਾਸ" || lower.contains("ardas") || lower.contains("ਅਰਦਾਸ") -> "ardas.json"
    t == "ਕੀਰਤਨ ਸੋਹਿਲਾ" || lower.contains("sohila") || lower.contains("ਸੋਹਿਲਾ") -> "kirtan_sohila.json"
    t == "ਆਰਤੀ" || lower.contains("aarti") || lower.contains("ਆਰਤੀ") -> "aarti.json"
    t == "ਆਸਾ ਦੀ ਵਾਰ" || lower.contains("asa_di_vaar") || lower.contains("asa di vaar") || lower.contains("ਆਸਾ ਦੀ ਵਾਰ") -> "asa_di_vaar.json"
    t == "ਸ੍ਰੀ ਸੁਖਮਨੀ ਸਾਹਿਬ" || lower.contains("sukhmani") || lower.contains("ਸੁਖਮਨੀ") -> "sri_sukhmani_sahib.json"
    else -> ""
  }
}

fun loadBaniFromAsset(context: android.content.Context, fileName: String): Bani {
  val sggsDb = com.example.data.SggsDatabase.getInstance(context)
  val stdTitleForFile = com.example.util.GurbaniUtils.getNitnemBaniTitle(fileName, "")

  sggsDb.getCachedNitnemBani(fileName)?.let { cached ->
    val correctTitle = stdTitleForFile ?: com.example.util.GurbaniUtils.getNitnemBaniTitle(fileName, cached.title) ?: cached.title
    if (cached.title != correctTitle) {
      val updated = cached.copy(title = correctTitle)
      sggsDb.putCachedNitnemBani(fileName, updated)
      return updated
    }
    return cached
  }

  return try {
    val jsonString = context.assets.open("bani/$fileName").bufferedReader().use { it.readText() }
    val jsonObject = org.json.JSONObject(jsonString)
    val rawTitle = jsonObject.optString("title", "")
    val stdTitle = stdTitleForFile ?: com.example.util.GurbaniUtils.getNitnemBaniTitle(fileName, rawTitle)
    val title = stdTitle ?: if (rawTitle.isNotEmpty() && !rawTitle.contains("Sahib") && !rawTitle.contains("Chaupai") && !rawTitle.contains("Rehraas")) convertGurbaniAkharToUnicode(rawTitle) else fileName
    val versesArray = jsonObject.getJSONArray("verses")
    val count = versesArray.length()
    val verses = ArrayList<Verse>(count)
    val lineIds = ArrayList<Int>(count)
    val rawLinesList = ArrayList<String>(count)

    for (i in 0 until count) {
      val verseObj = versesArray.getJSONObject(i)
      val id = verseObj.optInt("id", -1)
      if (id > 0) {
        lineIds.add(id)
      }
      val rawLine = verseObj.optString("line", "")
      if (rawLine.isNotEmpty()) {
        rawLinesList.add(rawLine)
      }
    }

    val punjabiMap = if (sggsDb.hasPunjabiMap()) sggsDb.getPunjabiTranslationMap() else emptyMap()

    val punjabiIdMap = if (lineIds.isNotEmpty()) {
      try {
        sggsDb.getPunjabiTranslationsForLineIds(lineIds)
      } catch (e: Exception) {
        emptyMap()
      }
    } else emptyMap()

    val punjabiLineMap = if (punjabiMap.isEmpty() && rawLinesList.isNotEmpty()) {
      try {
        sggsDb.getPunjabiTranslationsForLines(rawLinesList)
      } catch (e: Exception) {
        emptyMap()
      }
    } else emptyMap()

    for (i in 0 until count) {
      val verseObj = versesArray.getJSONObject(i)
      val id = verseObj.optInt("id", i)
      val rawLine = verseObj.getString("line")
      val line = convertGurbaniAkharToUnicode(rawLine)
      val pauseType = if (verseObj.has("pauseType")) verseObj.getString("pauseType") else null
      val bookmarked = verseObj.optBoolean("bookmarked", false)
      val translation = verseObj.optString("translation", "")
      var punjabiTranslation = verseObj.optString("punjabiTranslation", verseObj.optString("punjabi_translation", ""))

      if (punjabiTranslation.isEmpty()) {
        val cleanLine = line.replace("॥", "").replace("।", "").replace("|", "").trim()
        val cleanRaw = rawLine.replace("॥", "").replace("।", "").replace("|", "").trim()

        punjabiTranslation = punjabiMap[line]
          ?: punjabiMap[cleanLine]
          ?: punjabiMap[rawLine]
          ?: punjabiMap[cleanRaw]
          ?: (if (id > 0) punjabiIdMap[id] else null)
          ?: punjabiLineMap[line]
          ?: punjabiLineMap[cleanLine]
          ?: punjabiLineMap[rawLine]
          ?: punjabiLineMap[cleanRaw]
          ?: ""
      }

      verses.add(Verse(id = id, index = i, line = line, pauseType = pauseType, bookmarked = bookmarked, translation = translation, punjabiTranslation = punjabiTranslation))
    }
    val bani = Bani(fileName = fileName, title = title, verses = verses)
    sggsDb.putCachedNitnemBani(fileName, bani)
    bani
  } catch (e: Exception) {
    e.printStackTrace()
    val fallbackTitle = when (fileName) {
      "japji_sahib.json" -> "ਜਪੁਜੀ ਸਾਹਿਬ"
      "jaap_sahib.json" -> "ਜਾਪੁ ਸਾਹਿਬ"
      "tav_prasad_savaiye.json" -> "ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ"
      "chaupai_sahib.json" -> "ਚੌਪਈ ਸਾਹਿਬ"
      "anand_sahib.json" -> "ਅਨੰਦ ਸਾਹਿਬ"
      "rehras_sahib.json" -> "ਰਹਿਰਾਸ ਸਾਹਿਬ"
      "ardas.json" -> "ਅਰਦਾਸ"
      "kirtan_sohila.json" -> "ਕੀਰਤਨ ਸੋਹਿਲਾ"
      "aarti.json" -> "ਆਰਤੀ"
      "asa_di_vaar.json" -> "ਆਸਾ ਦੀ ਵਾਰ"
      "sri_sukhmani_sahib.json" -> "ਸ੍ਰੀ ਸੁਖਮਨੀ ਸਾਹਿਬ"
      else -> fileName.replace(".json", "").replace("_", " ").capitalize()
    }
    Bani(fileName = fileName, title = fallbackTitle, verses = emptyList())
  }
}

sealed class Screen {
  object Welcome : Screen()
  object Home : Screen()
  object Sggs : Screen()
  object Nitnem : Screen()
  object Search : Screen()
  object Bookmarks : Screen()
  object Settings : Screen()
  object About : Screen()
  data class BaniDetail(val name: String, val highlightIndex: Int? = null) : Screen()
}

@Composable
fun MainApp(viewModel: BookmarksViewModel, settingsManager: SettingsManager) {
  val settingsState by settingsManager.settings.collectAsStateWithLifecycle()
  var navigationStack by remember { mutableStateOf<List<Screen>>(emptyList()) }

  LaunchedEffect(settingsState.isFirstLaunchDone) {
    if (navigationStack.isEmpty()) {
      navigationStack = if (settingsState.isFirstLaunchDone) {
        listOf(Screen.Home)
      } else {
        listOf(Screen.Welcome)
      }
    }
  }

  if (navigationStack.isEmpty()) return

  val currentScreen = navigationStack.last()

  androidx.activity.compose.BackHandler(enabled = navigationStack.size > 1) {
    navigationStack = navigationStack.dropLast(1)
  }

  when (currentScreen) {
    is Screen.Welcome -> {
      WelcomeScreen(
        onGetStarted = {
          settingsManager.setFirstLaunchDone(true)
          navigationStack = listOf(Screen.Home)
        }
      )
    }
    is Screen.Home -> {
      HomeScreen(
        onNavigateToSggs = {
          navigationStack = navigationStack + Screen.Sggs
        },
        onNavigateToNitnem = {
          navigationStack = navigationStack + Screen.Nitnem
        },
        onNavigateToSearch = {
          navigationStack = navigationStack + Screen.Search
        },
        onNavigateToBookmarks = {
          navigationStack = navigationStack + Screen.Bookmarks
        },
        onNavigateToSettings = {
          navigationStack = navigationStack + Screen.Settings
        },
        onNavigateToAbout = {
          navigationStack = navigationStack + Screen.About
        }
      )
    }
    is Screen.Sggs -> {
      SggsScreen(
        onBack = {
          if (navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
          }
        },
        onNavigateToBani = { baniName ->
          navigationStack = navigationStack + Screen.BaniDetail(name = baniName)
        }
      )
    }
    is Screen.Nitnem -> {
      NitnemScreen(
        onBack = {
          if (navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
          }
        },
        onNavigateToBani = { baniName ->
          navigationStack = navigationStack + Screen.BaniDetail(name = baniName)
        }
      )
    }
    is Screen.Search -> {
      SearchScreen(
        settingsManager = settingsManager,
        onBack = {
          if (navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
          }
        },
        onNavigateToBani = { baniName, lineIndex ->
          navigationStack = navigationStack + Screen.BaniDetail(name = baniName, highlightIndex = lineIndex)
        }
      )
    }
    is Screen.Bookmarks -> {
      BookmarksScreen(
        viewModel = viewModel,
        onBack = {
          if (navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
          }
        },
        onNavigateToBani = { baniName, lineIndex ->
          navigationStack = navigationStack + Screen.BaniDetail(name = baniName, highlightIndex = lineIndex)
        }
      )
    }
    is Screen.BaniDetail -> {
      BaniDetailScreen(
        baniName = currentScreen.name,
        highlightIndex = currentScreen.highlightIndex,
        viewModel = viewModel,
        settingsManager = settingsManager,
        onBack = {
          if (navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
          }
        }
      )
    }
    is Screen.Settings -> {
      SettingsScreen(
        settingsManager = settingsManager,
        onBack = {
          if (navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
          }
        },
        onNavigateToAbout = {
          navigationStack = navigationStack + Screen.About
        }
      )
    }
    is Screen.About -> {
      AboutScreen(
        onBack = {
          if (navigationStack.size > 1) {
            navigationStack = navigationStack.dropLast(1)
          }
        }
      )
    }
  }
}

@Composable
fun HomeScreen(
  onNavigateToSggs: () -> Unit,
  onNavigateToNitnem: () -> Unit,
  onNavigateToSearch: () -> Unit,
  onNavigateToBookmarks: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToAbout: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  var visible by remember { mutableStateOf(false) }

  // Entrance animation trigger
  LaunchedEffect(Unit) {
    visible = true
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("home_screen_scaffold"),
    containerColor = Color.White,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(Color.White)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
      ) {
        // Upper section: Header, Title, and Accent Divider
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(top = 8.dp)
        ) {
          // Animated Ik Onkar symbol
          AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = spring()) + slideInVertically(
              initialOffsetY = { -40 },
              animationSpec = spring()
            )
          ) {
            Image(
              painter = painterResource(id = R.drawable.ek_onkar),
              contentDescription = "Ek Onkar",
              contentScale = androidx.compose.ui.layout.ContentScale.Fit,
              modifier = Modifier
                .size(120.dp)
                .padding(bottom = 4.dp)
                .testTag("ek_onkar_logo")
            )
          }

          // Animated App Title & Subtitle
          AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = spring()) + slideInVertically(
              initialOffsetY = { 40 },
              animationSpec = spring()
            )
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "ਗੁਰਬਾਣੀ ਖੋਜ",
                style = MaterialTheme.typography.headlineLarge.copy(
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 28.sp,
                  color = TextMedium,
                  letterSpacing = (-0.5).sp,
                  textAlign = TextAlign.Center
                ),
                modifier = Modifier.testTag("app_title")
              )
              
              Spacer(modifier = Modifier.height(6.dp))
              Box(
                modifier = Modifier
                  .width(48.dp)
                  .height(4.dp)
                  .background(
                    color = SaffronPrimary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(2.dp)
                  )
              )

              Spacer(modifier = Modifier.height(6.dp))
              
              Text(
                text = "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ ਅਤੇ ਨਿਤਨੇਮ ਪਾਠ",
                style = MaterialTheme.typography.bodyMedium.copy(
                  color = TextGray,
                  fontSize = 13.sp,
                  textAlign = TextAlign.Center
                ),
                modifier = Modifier.testTag("app_subtitle")
              )
            }
          }
        }

        // Middle section containing Polish Styled Menu Cards
        AnimatedVisibility(
          visible = visible,
          enter = fadeIn(animationSpec = spring(stiffness = 100f)) + slideInVertically(
            initialOffsetY = { 80 },
            animationSpec = spring(stiffness = 100f)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            val menuItems = listOf(
              MenuData(
                emoji = "🔍",
                title = "ਗੁਰਬਾਣੀ ਖੋਜ",
                testTag = "search_gurbani_button",
                toastMessage = "ਗੁਰਬਾਣੀ ਖੋਜ",
                buttonType = ButtonType.PRIMARY
              ),
              MenuData(
                emoji = "📖",
                title = "ਸ਼੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ",
                testTag = "sggs_button",
                toastMessage = "ਸ਼੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ",
                buttonType = ButtonType.SECONDARY
              ),
              MenuData(
                emoji = "📖",
                title = "ਨਿਤਨੇਮ ਸਾਹਿਬ",
                testTag = "nitnem_sahib_button",
                toastMessage = "ਨਿਤਨੇਮ ਸਾਹਿਬ",
                buttonType = ButtonType.SECONDARY
              ),
              MenuData(
                emoji = "⭐",
                title = "ਬੁੱਕਮਾਰਕ",
                testTag = "bookmarks_button",
                toastMessage = "ਬੁੱਕਮਾਰਕ",
                buttonType = ButtonType.SECONDARY
              ),
              MenuData(
                emoji = "⚙️",
                title = "ਸੈਟਿੰਗਾਂ",
                testTag = "settings_button",
                toastMessage = "ਸੈਟਿੰਗਾਂ",
                buttonType = ButtonType.DEFAULT
              ),
              MenuData(
                emoji = "ℹ️",
                title = "ਐਪ ਬਾਰੇ",
                testTag = "about_button",
                toastMessage = "ਐਪ ਬਾਰੇ",
                buttonType = ButtonType.DEFAULT
              )
            )

            menuItems.forEach { item ->
              HomeMenuItem(
                emoji = item.emoji,
                title = item.title,
                testTag = item.testTag,
                buttonType = item.buttonType,
                onClick = {
                  if (item.testTag == "sggs_button") {
                    onNavigateToSggs()
                  } else if (item.testTag == "nitnem_sahib_button") {
                    onNavigateToNitnem()
                  } else if (item.testTag == "search_gurbani_button") {
                    onNavigateToSearch()
                  } else if (item.testTag == "bookmarks_button") {
                    onNavigateToBookmarks()
                  } else if (item.testTag == "settings_button") {
                    onNavigateToSettings()
                  } else if (item.testTag == "about_button") {
                    onNavigateToAbout()
                  } else {
                    scope.launch {
                      snackbarHostState.currentSnackbarData?.dismiss()
                      snackbarHostState.showSnackbar(item.toastMessage)
                    }
                  }
                },
                modifier = Modifier.padding(vertical = 6.dp)
              )
            }
          }
        }

        // Footer Section: Material 3 Navigation Gesture Bar Placeholder / Accent
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp)
        ) {

          Text(
            text = "Made by Manjot Singh M.Aa*",
            style = MaterialTheme.typography.bodySmall.copy(
              color = TextGray,
              fontWeight = FontWeight.Medium,
              fontSize = 12.sp,
              textAlign = TextAlign.Center
            ),
            modifier = Modifier
              .padding(bottom = 12.dp)
              .testTag("home_made_by_credit")
          )
          
          // Slate navigation gesture indicator bar
          Box(
            modifier = Modifier
              .width(128.dp)
              .height(6.dp)
              .background(
                color = Slate200,
                shape = RoundedCornerShape(3.dp)
              )
          )
        }
      }
    }
  }
}

enum class ButtonType {
  PRIMARY, SECONDARY, DEFAULT
}

data class MenuData(
  val emoji: String,
  val title: String,
  val testTag: String,
  val toastMessage: String,
  val buttonType: ButtonType
)

@Composable
fun HomeMenuItem(
  emoji: String,
  title: String,
  testTag: String,
  buttonType: ButtonType,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  // Style properties based on Professional Polish Spec
  val containerColor = when (buttonType) {
    ButtonType.PRIMARY -> SaffronPrimary
    ButtonType.SECONDARY -> SaffronLight
    ButtonType.DEFAULT -> Slate50
  }

  val textColor = when (buttonType) {
    ButtonType.PRIMARY -> Color.White
    ButtonType.SECONDARY -> TextMedium
    ButtonType.DEFAULT -> TextMedium
  }

  val borderStroke = when (buttonType) {
    ButtonType.PRIMARY -> null
    ButtonType.SECONDARY -> BorderStroke(1.dp, SaffronBorder)
    ButtonType.DEFAULT -> BorderStroke(1.dp, Slate200)
  }

  val arrowColor = when (buttonType) {
    ButtonType.PRIMARY -> Color.White
    ButtonType.SECONDARY -> SaffronPrimary.copy(alpha = 0.8f)
    ButtonType.DEFAULT -> SaffronPrimary.copy(alpha = 0.6f)
  }

  val elevationDp = if (buttonType == ButtonType.PRIMARY) 4.dp else 0.dp

  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(
      containerColor = containerColor
    ),
    border = borderStroke,
    elevation = CardDefaults.cardElevation(defaultElevation = elevationDp),
    shape = RoundedCornerShape(24.dp), // rounded-3xl
    modifier = modifier
      .fillMaxWidth()
      .height(80.dp) // generous touch target and taller design matching HTML button padding
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Start
    ) {
      // Emoji with generous spacing
      Text(
        text = emoji,
        fontSize = 24.sp,
        modifier = Modifier.align(Alignment.CenterVertically)
      )

      Spacer(modifier = Modifier.width(16.dp))

      // Gurmukhi Title Text
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 19.sp,
          color = textColor
        )
      )

      Spacer(modifier = Modifier.weight(1f))

      // Minimalist forward arrow
      Text(
        text = "→",
        fontSize = 22.sp,
        color = arrowColor,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
  MyApplicationTheme {
    HomeScreen(
      onNavigateToSggs = {},
      onNavigateToNitnem = {},
      onNavigateToSearch = {},
      onNavigateToBookmarks = {},
      onNavigateToSettings = {},
      onNavigateToAbout = {}
    )
  }
}

@Composable
fun TopAppBar(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  actions: @Composable RowScope.() -> Unit = {}
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(64.dp)
      .padding(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(
      onClick = onBack,
      modifier = Modifier
        .size(48.dp)
        .testTag("back_button")
    ) {
      Text(
        text = "←",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = SaffronPrimary
      )
    }
    
    Spacer(modifier = Modifier.width(12.dp))
    
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = TextMedium
      ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .testTag("app_bar_title")
    )

    actions()
  }
}

fun convertToGurmukhiNumeral(number: Int): String {
  val gurmukhiDigits = charArrayOf('੦', '੧', '੨', '੩', '੪', '੫', '੬', '੭', '੮', '੯')
  val sb = StringBuilder()
  val str = number.toString()
  for (ch in str) {
    if (ch in '0'..'9') {
      sb.append(gurmukhiDigits[ch - '0'])
    } else {
      sb.append(ch)
    }
  }
  return sb.toString()
}

@Composable
fun SggsScreen(
  onBack: () -> Unit,
  onNavigateToBani: (String) -> Unit
) {
  var visible by remember { mutableStateOf(false) }
  var angSearchQuery by remember { mutableStateOf("") }

  LaunchedEffect(Unit) {
    visible = true
  }

  val filteredAngs = remember(angSearchQuery) {
    val queryNum = angSearchQuery.filter { it.isDigit() }.toIntOrNull()
    if (queryNum != null) {
      listOf(queryNum.coerceIn(1, 1430))
    } else {
      (1..1430).toList()
    }
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("sggs_screen_scaffold"),
    containerColor = Color.White
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp)
    ) {
      TopAppBar(
        title = "📖 ਸ਼੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ",
        onBack = onBack,
        modifier = Modifier.padding(top = 8.dp)
      )

      Card(
        colors = CardDefaults.cardColors(containerColor = Slate50),
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
            text = "ਅੰਗ ਨੰਬਰ ਤੇ ਜਾਓ (1 - 1430)",
            style = MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.Bold,
              color = TextMedium
            ),
            modifier = Modifier.padding(bottom = 6.dp)
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            OutlinedTextField(
              value = angSearchQuery,
              onValueChange = { input ->
                val digits = input.filter { it.isDigit() }
                if (digits.isEmpty() || (digits.toIntOrNull() ?: 0) in 1..1430) {
                  angSearchQuery = digits
                }
              },
              placeholder = { Text("ਉਦਾਹਰਨ: 1, 262, 1430", fontSize = 13.sp, color = TextGray) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
              keyboardActions = KeyboardActions(onGo = {
                val targetAng = angSearchQuery.toIntOrNull() ?: 1
                onNavigateToBani("sggs_ang_$targetAng")
              }),
              modifier = Modifier
                .weight(1f)
                .testTag("sggs_ang_input_field"),
              colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = SaffronPrimary,
                unfocusedIndicatorColor = Slate200
              ),
              shape = RoundedCornerShape(12.dp)
            )

            Button(
              onClick = {
                val targetAng = angSearchQuery.toIntOrNull() ?: 1
                onNavigateToBani("sggs_ang_$targetAng")
              },
              colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary, contentColor = Color.White),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .height(52.dp)
                .testTag("sggs_go_button")
            ) {
              Text("ਪੜ੍ਹੋ 📖", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "ਮੁੱਖ ਅੰਗ ਸ਼ਾਰਟਕੱਟ:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextGray
          )
          Spacer(modifier = Modifier.height(4.dp))
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
          ) {
            val shortcuts = listOf(
              "ਅੰਗ ੧ (ਜਪੁਜੀ ਸਾਹਿਬ)" to 1,
              "ਅੰਗ ੯ (ਸੋ ਦਰੁ)" to 9,
              "ਅੰਗ ੧੨ (ਸੋ ਪੁਰਖੁ)" to 12,
              "ਅੰਗ ੨੬੨ (ਸੁਖਮਨੀ ਸਾਹਿਬ)" to 262,
              "ਅੰਗ ੯੧੭ (ਅਨੰਦੁ ਸਾਹਿਬ)" to 917,
              "ਅੰਗ ੧੪੨੬ (ਸਲੋਕ ਮਹਲਾ ੯)" to 1426
            )
            items(shortcuts) { (label, ang) ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(20.dp))
                  .background(SaffronLight)
                  .border(1.dp, SaffronBorder, RoundedCornerShape(20.dp))
                  .clickable { onNavigateToBani("sggs_ang_$ang") }
                  .padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Text(
                  text = label,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextMedium
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "ਸੰਪੂਰਨ ੧੪੩੦ ਅੰਗ (All 1430 Angs)",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextMedium
          )
        )
        Text(
          text = "${filteredAngs.size} ਅੰਗ",
          fontSize = 12.sp,
          color = TextGray,
          fontWeight = FontWeight.SemiBold
        )
      }

      AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring()) + slideInVertically(
          initialOffsetY = { 40 },
          animationSpec = spring()
        ),
        modifier = Modifier.weight(1f)
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .testTag("sggs_angs_list"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(vertical = 8.dp)
        ) {
          items(filteredAngs, key = { it }) { angNum ->
            val gurmukhiNumeral = convertToGurmukhiNumeral(angNum)
            Card(
              onClick = { onNavigateToBani("sggs_ang_$angNum") },
              colors = CardDefaults.cardColors(containerColor = Slate50),
              border = BorderStroke(1.dp, Slate200),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("ang_item_$angNum")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(42.dp)
                      .clip(CircleShape)
                      .background(SaffronLight)
                      .border(1.dp, SaffronBorder, CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = gurmukhiNumeral,
                      fontSize = 15.sp,
                      fontWeight = FontWeight.Bold,
                      color = SaffronPrimary
                    )
                  }

                  Column {
                    Text(
                      text = "ਅੰਗ $gurmukhiNumeral",
                      style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextMedium
                      )
                    )
                    Text(
                      text = "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ • Ang $angNum",
                      fontSize = 12.sp,
                      color = TextGray
                    )
                  }
                }

                Text(
                  text = "ਅੱਗੇ ➔",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = SaffronPrimary
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun NitnemScreen(
  onBack: () -> Unit,
  onNavigateToBani: (String) -> Unit
) {
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    visible = true
  }

  val banis = listOf(
    "ਜਪੁਜੀ ਸਾਹਿਬ",
    "ਜਾਪੁ ਸਾਹਿਬ",
    "ਤ੍ਵ ਪ੍ਰਸਾਦਿ ਸਵੱਯੇ",
    "ਚੌਪਈ ਸਾਹਿਬ",
    "ਅਨੰਦ ਸਾਹਿਬ",
    "ਰਹਿਰਾਸ ਸਾਹਿਬ",
    "ਅਰਦਾਸ",
    "ਕੀਰਤਨ ਸੋਹਿਲਾ",
    "ਆਰਤੀ",
    "ਆਸਾ ਦੀ ਵਾਰ",
    "ਸ੍ਰੀ ਸੁਖਮਨੀ ਸਾਹਿਬ"
  )

  val gurmukhiNumerals = listOf("੧", "੨", "੩", "੪", "੫", "੬", "੭", "੮", "੯", "੧੦", "੧੧")

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("nitnem_screen_scaffold"),
    containerColor = Color.White
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp)
    ) {
      TopAppBar(
        title = "📖 ਨਿਤਨੇਮ ਸਾਹਿਬ",
        onBack = onBack,
        modifier = Modifier.padding(top = 8.dp)
      )

      AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring()) + slideInVertically(
          initialOffsetY = { 40 },
          animationSpec = spring()
        ),
        modifier = Modifier.weight(1f)
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .testTag("banis_list"),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
          itemsIndexed(banis) { index, bani ->
            val numeral = gurmukhiNumerals.getOrElse(index) { "${index + 1}" }
            BaniCard(
              numeral = numeral,
              title = bani,
              testTag = "bani_item_${index}",
              onClick = { onNavigateToBani(bani) }
            )
          }
        }
      }

      // Bottom polish indicator spacer to match visual design
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .width(128.dp)
            .height(6.dp)
            .background(
              color = Slate200,
              shape = RoundedCornerShape(3.dp)
            )
        )
      }
    }
  }
}

@Composable
fun BaniCard(
  numeral: String,
  title: String,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(
      containerColor = Slate50
    ),
    border = BorderStroke(1.dp, Slate200),
    shape = RoundedCornerShape(20.dp),
    modifier = modifier
      .fillMaxWidth()
      .height(72.dp)
      .testTag(testTag)
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Gurmukhi Numeral in custom colored circle
      Box(
        modifier = Modifier
          .size(40.dp)
          .background(SaffronLight, shape = RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = numeral,
          style = MaterialTheme.typography.bodyMedium.copy(
            color = SaffronDark,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )
        )
      }

      Spacer(modifier = Modifier.width(16.dp))

      // Bani Name
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = TextMedium
        ),
        modifier = Modifier.weight(1f)
      )

      // Arrow indicator matching Polish style
      Text(
        text = "→",
        fontSize = 20.sp,
        color = SaffronPrimary.copy(alpha = 0.7f),
        fontWeight = FontWeight.Bold
      )
    }
  }
}

val santLipiFontFamily = FontFamily.Serif
val notoSerifGurmukhiFontFamily = FontFamily.Serif

@Composable
fun buildGurmukhiLine(line: String, vishramColorHex: String): androidx.compose.ui.text.AnnotatedString {
  val color = try {
    if (vishramColorHex.isNotEmpty() && vishramColorHex != "None" && vishramColorHex != "none") {
      Color(android.graphics.Color.parseColor(vishramColorHex))
    } else {
      Color.Unspecified
    }
  } catch (e: Exception) {
    Color.Unspecified
  }

  if (color == Color.Unspecified) {
    return androidx.compose.ui.text.AnnotatedString(line)
  }

  return buildAnnotatedString {
    var startIndex = 0
    while (startIndex < line.length) {
      val dandaIndex = line.indexOf('।', startIndex)
      val doubleDandaIndex = line.indexOf('॥', startIndex)

      val nextIndex = when {
        dandaIndex >= 0 && doubleDandaIndex >= 0 -> minOf(dandaIndex, doubleDandaIndex)
        dandaIndex >= 0 -> dandaIndex
        doubleDandaIndex >= 0 -> doubleDandaIndex
        else -> -1
      }

      if (nextIndex == -1) {
        append(line.substring(startIndex))
        break
      }

      append(line.substring(startIndex, nextIndex))
      withStyle(style = SpanStyle(color = color)) {
        append(line[nextIndex])
      }
      startIndex = nextIndex + 1
    }
  }
}

fun findAshtpadiIndex(verses: List<Verse>, num: Int): Int {
  if (num < 1 || num > 24) return -1
  if (num == 1) return 0

  val slokIndices = verses.indices.filter { idx ->
    val l = verses[idx].line.trim()
    l == "ਸਲੋਕੁ ॥" || l.startsWith("ਸਲੋਕੁ")
  }
  if (num <= slokIndices.size) {
    return slokIndices[num - 1]
  }

  val ashtpadiIndices = verses.indices.filter { idx ->
    verses[idx].line.contains("ਅਸਟਪਦੀ")
  }
  if (num <= ashtpadiIndices.size) {
    return ashtpadiIndices[num - 1]
  }

  return -1
}

@Composable
fun BaniDetailScreen(
  baniName: String,
  highlightIndex: Int? = null,
  viewModel: BookmarksViewModel,
  settingsManager: SettingsManager,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val settingsState by settingsManager.settings.collectAsStateWithLifecycle()

  val isSukhmaniSahib = remember(baniName) {
    baniName.contains("ਸੁਖਮਨੀ") || baniName.lowercase().contains("sukhmani")
  }

  var showAshtpadiDialog by remember { mutableStateOf(false) }
  var tempAshtpadiHighlightIndex by remember { mutableStateOf<Int?>(null) }

  LaunchedEffect(tempAshtpadiHighlightIndex) {
    if (tempAshtpadiHighlightIndex != null) {
      kotlinx.coroutines.delay(3000)
      tempAshtpadiHighlightIndex = null
    }
  }

  val fontSize = when (settingsState.fontSize) {
    "Small" -> 15.sp
    "Large" -> 23.sp
    "Extra Large" -> 28.sp
    else -> 19.sp // Medium
  }

  val fontFamily = when (settingsState.fontFamily) {
    "Sant Lipi" -> santLipiFontFamily
    "Unicode Gurmukhi" -> FontFamily.SansSerif
    "Noto Serif Gurmukhi" -> notoSerifGurmukhiFontFamily
    else -> notoSerifGurmukhiFontFamily
  }

  val lineSpacingMultiplier = settingsState.lineSpacing
  val lineHeight = fontSize * lineSpacingMultiplier

  val initialAngNum = remember(baniName) {
    if (baniName.startsWith("sggs_ang_") || baniName.contains("ਅੰਗ")) {
      val digits = baniName.filter { it.isDigit() || it in '੦'..'੯' }
      digits.toIntOrNull() ?: com.example.util.GurbaniUtils.convertGurmukhiNumeralToDecimal(digits) ?: 1
    } else null
  }
  var activeAngNum by remember(baniName) { mutableStateOf(initialAngNum) }

  val sggsDb = remember { SggsDatabase.getInstance(context) }
  val stdNitnemTitle = remember(baniName, activeAngNum) {
    if (activeAngNum != null || baniName.startsWith("sggs_shabad_")) null
    else {
      val fn = getBaniFileName(baniName)
      com.example.util.GurbaniUtils.getNitnemBaniTitle(fn, baniName)
    }
  }

  val initialCachedData = remember(baniName, activeAngNum) {
    if (activeAngNum != null) {
      val cachedVerses = sggsDb.getCachedAngVerses(activeAngNum!!)
      if (cachedVerses != null) {
        val gurmukhiNumeral = convertToGurmukhiNumeral(activeAngNum!!)
        val title = "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ (ਅੰਗ $gurmukhiNumeral)"
        Pair(cachedVerses, title)
      } else null
    } else if (baniName.startsWith("sggs_shabad_")) {
      val shabadId = baniName.removePrefix("sggs_shabad_")
      val cachedPair = sggsDb.getCachedShabadVerses(shabadId)
      if (cachedPair != null) {
        Pair(cachedPair.first, cachedPair.second)
      } else null
    } else {
      val fileName = getBaniFileName(baniName)
      if (fileName.isNotEmpty()) {
        val cachedNitnem = sggsDb.getCachedNitnemBani(fileName)
        if (cachedNitnem != null && cachedNitnem.verses.isNotEmpty()) {
          val title = stdNitnemTitle ?: cachedNitnem.title
          Pair(cachedNitnem.verses, title)
        } else null
      } else null
    }
  }

  var sggsVerses by remember(baniName, activeAngNum) { mutableStateOf(initialCachedData?.first ?: emptyList()) }
  var dynamicBaniTitle by remember(baniName, activeAngNum) {
    mutableStateOf(stdNitnemTitle ?: initialCachedData?.second ?: baniName)
  }
  var isSggsLoading by remember(baniName, activeAngNum) {
    mutableStateOf(initialCachedData == null)
  }
  var showLoadingUI by remember(baniName, activeAngNum) { mutableStateOf(false) }
  var sggsErrorMessage by remember(baniName, activeAngNum) { mutableStateOf<String?>(null) }

  val effectiveFileName = remember(baniName, activeAngNum) {
    if (activeAngNum != null) {
      "sggs_ang_$activeAngNum"
    } else if (baniName.startsWith("sggs_shabad_")) {
      baniName
    } else {
      getBaniFileName(baniName)
    }
  }

  val bani = remember(baniName, dynamicBaniTitle, sggsVerses, effectiveFileName) {
    if (sggsVerses.isNotEmpty()) {
      Bani(effectiveFileName, dynamicBaniTitle, sggsVerses)
    } else {
      val fileName = getBaniFileName(baniName)
      if (fileName.isNotEmpty()) {
        val cached = sggsDb.getCachedNitnemBani(fileName)
        if (cached != null) {
          cached
        } else {
          Bani("", baniName, emptyList())
        }
      } else {
        Bani("", baniName, emptyList())
      }
    }
  }

  LaunchedEffect(baniName, activeAngNum) {
    sggsErrorMessage = null
    if (initialCachedData != null) {
      isSggsLoading = false
      showLoadingUI = false
      return@LaunchedEffect
    }

    isSggsLoading = true
    showLoadingUI = false

    val timerJob = launch {
      kotlinx.coroutines.delay(100)
      if (isSggsLoading) {
        showLoadingUI = true
      }
    }

    try {
      withContext(Dispatchers.IO) {
        if (activeAngNum != null) {
          val currentAng = activeAngNum!!
          val gurmukhiNumeral = convertToGurmukhiNumeral(currentAng)
          val title = "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ (ਅੰਗ $gurmukhiNumeral)"
          val entities = sggsDb.searchByAng(currentAng)
          if (entities.isNotEmpty()) {
            val verses = entities.mapIndexed { idx, item ->
              Verse(
                id = item.line.id.toIntOrNull() ?: item.line.id.hashCode(),
                index = idx,
                line = convertGurbaniAkharToUnicode(item.line.gurmukhi),
                translation = item.translation ?: item.line.translation,
                punjabiTranslation = item.punjabiTranslation ?: item.line.punjabiTranslation
              )
            }
            sggsDb.putCachedAngVerses(currentAng, verses)
            withContext(Dispatchers.Main) {
              dynamicBaniTitle = title
              sggsVerses = verses
              isSggsLoading = false
              showLoadingUI = false
              timerJob.cancel()
            }
          } else {
            withContext(Dispatchers.Main) {
              isSggsLoading = false
              showLoadingUI = false
              timerJob.cancel()
              sggsErrorMessage = "ਅੰਗ $currentAng ਲਈ ਕੋਈ ਬਾਣੀ ਨਹੀਂ ਮਿਲੀ।"
            }
          }
        } else if (baniName.startsWith("sggs_shabad_")) {
          val shabadId = baniName.removePrefix("sggs_shabad_")
          val lineEntities = sggsDb.getShabadByShabadId(shabadId)
          if (lineEntities.isNotEmpty()) {
            val firstLine = lineEntities.first().line
            val raagSuffix = if (firstLine.raag.isNotEmpty()) " • ${firstLine.raag}" else ""
            val title = "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ (ਅੰਗ ${convertToGurmukhiNumeral(firstLine.source_page)})$raagSuffix"
            val verses = lineEntities.mapIndexed { idx, item ->
              Verse(
                id = item.line.id.toIntOrNull() ?: item.line.id.hashCode(),
                index = idx,
                line = convertGurbaniAkharToUnicode(item.line.gurmukhi),
                translation = item.translation ?: item.line.translation,
                punjabiTranslation = item.punjabiTranslation ?: item.line.punjabiTranslation
              )
            }
            sggsDb.putCachedShabadVerses(shabadId, verses, title)
            withContext(Dispatchers.Main) {
              dynamicBaniTitle = title
              sggsVerses = verses
              isSggsLoading = false
              showLoadingUI = false
              timerJob.cancel()
            }
          } else {
            withContext(Dispatchers.Main) {
              isSggsLoading = false
              showLoadingUI = false
              timerJob.cancel()
              sggsErrorMessage = "ਸ਼ਬਦ $shabadId ਡਾਟਾਬੇਸ ਵਿੱਚ ਨਹੀਂ ਮਿਲਿਆ।"
            }
          }
        } else {
          val fileName = getBaniFileName(baniName)
          if (fileName.isNotEmpty()) {
            val nitnemBani = loadBaniFromAsset(context, fileName)
            withContext(Dispatchers.Main) {
              dynamicBaniTitle = stdNitnemTitle ?: nitnemBani.title
              sggsVerses = nitnemBani.verses
              isSggsLoading = false
              showLoadingUI = false
              timerJob.cancel()
            }
          } else {
            withContext(Dispatchers.Main) {
              isSggsLoading = false
              showLoadingUI = false
              timerJob.cancel()
              sggsErrorMessage = "ਇਸ ਬਾਣੀ ਦਾ ਪਾਠ ਉਪਲਬਧ ਨਹੀਂ ਹੈ।"
            }
          }
        }
      }
    } catch (e: Exception) {
      android.util.Log.e("BaniDetailScreen", "Error loading Bani content: ${e.message}", e)
      withContext(Dispatchers.Main) {
        isSggsLoading = false
        showLoadingUI = false
        timerJob.cancel()
        sggsErrorMessage = "ਤਰੁੱਟੀ: ${e.localizedMessage ?: e.message}"
      }
    }
  }

  val listState = rememberLazyListState()

  // Last read position handling
  val lastReadPosFlow = remember(bani.fileName) {
    settingsManager.getLastReadPosition(bani.fileName)
  }
  val lastReadIndex by lastReadPosFlow.collectAsStateWithLifecycle(initialValue = 0)

  var isNavigatedByButtons by remember { mutableStateOf(false) }
  val activeHighlightIndex = if (!isNavigatedByButtons && (activeAngNum == null || activeAngNum == initialAngNum)) highlightIndex else null

  LaunchedEffect(activeAngNum) {
    if (activeAngNum != null) {
      listState.scrollToItem(0)
    }
  }

  // Scroll to activeHighlightIndex if present, otherwise for non-Ang banis restore last read position or scroll to top (0)
  var hasInitialScrolled by remember(bani.fileName, activeHighlightIndex) { mutableStateOf(false) }
  LaunchedEffect(bani.fileName, bani.verses.size, activeHighlightIndex, lastReadIndex) {
    if (!hasInitialScrolled && bani.verses.isNotEmpty()) {
      val targetIndex = when {
        activeHighlightIndex != null && activeHighlightIndex >= 0 && activeHighlightIndex < bani.verses.size -> activeHighlightIndex
        activeAngNum == null && lastReadIndex > 0 && lastReadIndex < bani.verses.size -> lastReadIndex
        else -> 0
      }
      listState.scrollToItem(targetIndex)
      hasInitialScrolled = true
    }
  }

  // Save last read position as user scrolls
  LaunchedEffect(listState, bani.fileName) {
    snapshotFlow { listState.firstVisibleItemIndex }
      .collect { firstIndex ->
        if (hasInitialScrolled && firstIndex >= 0 && bani.fileName.isNotEmpty()) {
          settingsManager.saveLastReadPosition(bani.fileName, firstIndex)
        }
      }
  }

  val bookmarks by viewModel.allBookmarks.collectAsStateWithLifecycle()

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("bani_detail_screen_scaffold"),
    containerColor = Color.White
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp)
    ) {
      TopAppBar(
        title = stdNitnemTitle ?: if (dynamicBaniTitle.isNotEmpty()) dynamicBaniTitle else (com.example.util.GurbaniUtils.cleanUserFriendlyTitle(baniName).ifEmpty { "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ" }),
        onBack = onBack,
        actions = {
          if (isSukhmaniSahib) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SaffronLight)
                .border(1.dp, SaffronBorder, RoundedCornerShape(12.dp))
                .clickable { showAshtpadiDialog = true }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("top_bar_ashtpadi_button")
            ) {
              Text(
                text = "📖 Jump",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SaffronDark
              )
            }
          }
        },
        modifier = Modifier.padding(top = 8.dp)
      )

      if (isSukhmaniSahib) {
        if (showAshtpadiDialog) {
          var inputText by remember { mutableStateOf("") }
          var errorMessage by remember { mutableStateOf<String?>(null) }

          AlertDialog(
            onDismissRequest = {
              showAshtpadiDialog = false
              errorMessage = null
            },
            title = {
              Text(
                text = "Jump to Ashtpadi",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextMedium,
                  fontSize = 18.sp
                ),
                modifier = Modifier.testTag("ashtpadi_dialog_title")
              )
            },
            text = {
              Column {
                Text(
                  text = "Enter Ashtpadi number (1 to 24):",
                  fontSize = 13.sp,
                  color = TextGray
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                  value = inputText,
                  onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }
                    inputText = digits
                    if (errorMessage != null) {
                      errorMessage = null
                    }
                  },
                  label = { Text("Ashtpadi Number") },
                  placeholder = { Text("1 - 24") },
                  singleLine = true,
                  isError = errorMessage != null,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  colors = TextFieldDefaults.colors(
                    focusedContainerColor = Slate50,
                    unfocusedContainerColor = Slate50,
                    focusedIndicatorColor = SaffronPrimary,
                    unfocusedIndicatorColor = Slate200,
                    focusedLabelColor = SaffronPrimary
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ashtpadi_number_input")
                )
                if (errorMessage != null) {
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("ashtpadi_error_message")
                  )
                }
              }
            },
            confirmButton = {
              Button(
                onClick = {
                  val num = inputText.toIntOrNull()
                  if (num != null && num in 1..24) {
                    val targetIndex = findAshtpadiIndex(bani.verses, num)
                    if (targetIndex >= 0 && targetIndex < bani.verses.size) {
                      coroutineScope.launch {
                        listState.animateScrollToItem(targetIndex)
                      }
                      tempAshtpadiHighlightIndex = targetIndex
                      showAshtpadiDialog = false
                      errorMessage = null
                    } else {
                      errorMessage = "Please enter an Ashtpadi number between 1 and 24."
                    }
                  } else {
                    errorMessage = "Please enter an Ashtpadi number between 1 and 24."
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                modifier = Modifier.testTag("ashtpadi_go_button")
              ) {
                Text("Go", color = Color.White, fontWeight = FontWeight.Bold)
              }
            },
            dismissButton = {
              TextButton(
                onClick = {
                  showAshtpadiDialog = false
                  errorMessage = null
                },
                modifier = Modifier.testTag("ashtpadi_cancel_button")
              ) {
                Text("Cancel", color = TextGray)
              }
            }
          )
        }

        Card(
          colors = CardDefaults.cardColors(containerColor = SaffronLight),
          border = BorderStroke(1.dp, SaffronBorder),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { showAshtpadiDialog = true }
            .testTag("jump_to_ashtpadi_button")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text("📖", fontSize = 16.sp)
              Text(
                text = "Jump to Ashtpadi (ਅਸਟਪਦੀ)",
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = SaffronDark,
                  fontSize = 13.sp
                )
              )
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, SaffronPrimary, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text(
                text = "1 - 24 ▾",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SaffronPrimary
              )
            }
          }
        }
      }

      if (activeAngNum != null) {
        val currentAng = activeAngNum!!
        var showAngDialog by remember { mutableStateOf(false) }

        if (showAngDialog) {
          var dialogInput by remember { mutableStateOf("$currentAng") }
          AlertDialog(
            onDismissRequest = { showAngDialog = false },
            title = { Text("ਅੰਗ ਚੁਣੋ (1 - 1430)", fontWeight = FontWeight.Bold) },
            text = {
              OutlinedTextField(
                value = dialogInput,
                onValueChange = { input ->
                  val digits = input.filter { it.isDigit() }
                  if (digits.isEmpty() || (digits.toIntOrNull() ?: 0) in 1..1430) {
                    dialogInput = digits
                  }
                },
                label = { Text("ਅੰਗ ਨੰਬਰ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
              )
            },
            confirmButton = {
              Button(
                onClick = {
                  val num = dialogInput.toIntOrNull()
                  if (num != null && num in 1..1430) {
                    isNavigatedByButtons = true
                    activeAngNum = num
                  }
                  showAngDialog = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
              ) {
                Text("ਜਾਓ 📖", color = Color.White)
              }
            },
            dismissButton = {
              TextButton(onClick = { showAngDialog = false }) {
                Text("ਰੱਦ ਕਰੋ", color = TextGray)
              }
            }
          )
        }

        Card(
          colors = CardDefaults.cardColors(containerColor = SaffronLight),
          border = BorderStroke(1.dp, SaffronBorder),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("sggs_ang_navigation_bar")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TextButton(
              onClick = {
                if (currentAng > 1) {
                  isNavigatedByButtons = true
                  activeAngNum = currentAng - 1
                }
              },
              enabled = currentAng > 1,
              modifier = Modifier.testTag("prev_ang_button")
            ) {
              Text("⏮️ ਪਿਛਲਾ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (currentAng > 1) SaffronDark else TextGray)
            }

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, SaffronPrimary, RoundedCornerShape(12.dp))
                .clickable { showAngDialog = true }
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("current_ang_badge")
            ) {
              Text(
                text = "ਅੰਗ ${convertToGurmukhiNumeral(currentAng)} / ੧੪੩੦ ▾",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SaffronDark
              )
            }

            TextButton(
              onClick = {
                if (currentAng < 1430) {
                  isNavigatedByButtons = true
                  activeAngNum = currentAng + 1
                }
              },
              enabled = currentAng < 1430,
              modifier = Modifier.testTag("next_ang_button")
            ) {
              Text("ਅਗਲਾ ⏭️", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (currentAng < 1430) SaffronDark else TextGray)
            }
          }
        }
      }

      if (bani.verses.isEmpty()) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = SaffronLight
            ),
            border = BorderStroke(1.dp, SaffronBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp)
              .testTag("empty_bani_card")
          ) {
            Column(
              modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Text(
                text = "ੴ",
                style = MaterialTheme.typography.displayMedium.copy(
                  fontSize = 48.sp,
                  color = SaffronPrimary,
                  fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 16.dp)
              )

              Text(
                text = "ਵਾਹਿਗੁਰੂ ਜੀ ਕਾ ਖਾਲਸਾ\nਵਾਹਿਗੁਰੂ ਜੀ ਕੀ ਫਤਿਹ॥",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 18.sp,
                  color = TextMedium,
                  textAlign = TextAlign.Center,
                  lineHeight = 24.sp
                ),
                modifier = Modifier.padding(bottom = 12.dp)
              )

              if (isSggsLoading) {
                if (showLoadingUI) {
                  CircularProgressIndicator(
                    color = SaffronPrimary,
                    modifier = Modifier
                      .size(36.dp)
                      .padding(bottom = 12.dp)
                  )

                  Text(
                    text = "ਬਾਣੀ ਲੋਡ ਹੋ ਰਹੀ ਹੈ...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                      color = TextGray,
                      fontSize = 14.sp,
                      textAlign = TextAlign.Center,
                      lineHeight = 20.sp
                    )
                  )
                }
              } else {
                Text(
                  text = sggsErrorMessage ?: "ਇਸ ਬਾਣੀ ਦਾ ਪਾਠ ਉਪਲਬਧ ਨਹੀਂ ਹੈ।",
                  style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                  )
                )
              }
            }
          }
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .testTag("bani_verses_list"),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
          itemsIndexed(bani.verses, key = { index, _ -> index }) { index, verse ->
            val isHighlighted = index == activeHighlightIndex || index == tempAshtpadiHighlightIndex
            val isBookmarked = bookmarks.any { it.fileName == bani.fileName && it.lineIndex == index }

            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (isHighlighted) SaffronLight else Slate50
              ),
              border = BorderStroke(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = if (isHighlighted) SaffronPrimary else Slate200
              ),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("verse_card_$index")
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp)
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 80.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  // Gurmukhi verse line
                  Text(
                    text = buildGurmukhiLine(verse.line, settingsState.vishramColor),
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontWeight = FontWeight.Bold,
                      fontSize = fontSize,
                      fontFamily = fontFamily,
                      color = TextMedium,
                      textAlign = TextAlign.Center,
                      lineHeight = lineHeight
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("verse_line_$index")
                  )

                  if (verse.translation.isNotEmpty() && settingsState.showTranslation) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // English Translation line
                    Text(
                      text = verse.translation,
                      style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                      ),
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verse_translation_$index")
                    )
                  }

                  if (settingsState.showPunjabiTranslation) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val pText = if (verse.punjabiTranslation.isNotEmpty()) verse.punjabiTranslation else "ਪੰਜਾਬੀ ਟੀਕਾ ਉਪਲਬਧ ਨਹੀਂ ਹੈ"
                    // Punjabi Translation line
                    Text(
                      text = pText,
                      style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = TextGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                      ),
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verse_punjabi_translation_$index")
                    )
                  }
                }

                // Action buttons: Share, Bookmark
                Row(
                  modifier = Modifier
                    .align(Alignment.TopEnd),
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  // Share Verse Button
                  IconButton(
                    onClick = {
                      val sb = StringBuilder(verse.line)
                      if (verse.translation.isNotEmpty() && settingsState.showTranslation) {
                        sb.append("\n").append(verse.translation)
                      }
                      if (verse.punjabiTranslation.isNotEmpty() && settingsState.showPunjabiTranslation) {
                        sb.append("\n").append(verse.punjabiTranslation)
                      }
                      sb.append("\n\n— ਗੁਰਬਾਣੀ ਖੋਜ ($baniName)")
                      val shareText = sb.toString()
                      val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                      }
                      context.startActivity(Intent.createChooser(shareIntent, "ਤੁਕ ਸਾਂਝੀ ਕਰੋ"))
                    },
                    modifier = Modifier
                      .size(32.dp)
                      .testTag("share_icon_$index")
                  ) {
                    Text(text = "📤", fontSize = 15.sp)
                  }

                  // Bookmark Icon Button
                  IconButton(
                    onClick = {
                      val cleanBaniTitle = if (dynamicBaniTitle.isNotBlank() && !com.example.util.GurbaniUtils.isInternalId(dynamicBaniTitle)) dynamicBaniTitle else bani.title
                      viewModel.toggleBookmark(
                        baniName = cleanBaniTitle,
                        fileName = bani.fileName,
                        lineIndex = index,
                        verseLine = verse.line,
                        translation = verse.translation
                      ) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                      }
                    },
                    modifier = Modifier
                      .size(32.dp)
                      .testTag("bookmark_icon_$index")
                  ) {
                    Text(
                      text = if (isBookmarked) "⭐" else "☆",
                      fontSize = 18.sp
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Bottom gesture line
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .width(128.dp)
            .height(6.dp)
            .background(
              color = Slate200,
              shape = RoundedCornerShape(3.dp)
            )
        )
      }
    }
  }
}

fun getVerseAng(fileName: String, verseIndex: Int, totalVerses: Int): Int {
  if (totalVerses <= 0) return 1
  return when (fileName) {
    "japji_sahib.json" -> 1 + minOf(7, (verseIndex * 8) / totalVerses)
    "kirtan_sohila.json" -> 12 + minOf(1, (verseIndex * 2) / totalVerses)
    "sri_sukhmani_sahib.json" -> 262 + minOf(34, (verseIndex * 35) / totalVerses)
    "asa_di_vaar.json" -> 462 + minOf(13, (verseIndex * 14) / totalVerses)
    "anand_sahib.json" -> 917 + minOf(5, (verseIndex * 6) / totalVerses)
    "aarti.json" -> {
      when {
        verseIndex < 45 -> 13
        verseIndex < 90 -> 663
        verseIndex < 135 -> 694
        verseIndex < 180 -> 794
        verseIndex < 220 -> 1163
        else -> 1353
      }
    }
    else -> 1
  }
}

fun normalizeGurmukhiChar(ch: Char): Char {
  return when (ch) {
    'ਆ', 'ਇ', 'ਈ', 'ਏ', 'ਐ', 'ਉ', 'ਊ', 'ਓ', 'ਔ', 'ੳ', 'ੲ' -> 'ਅ'
    else -> ch
  }
}

fun convertGurmukhiToGurbaniAkharAscii(input: String): String {
  val map = mapOf(
    'ੴ' to "<>",
    'ੳ' to "a", 'ਅ' to "A", 'ੲ' to "e",
    'ਸ' to "s", 'ਹ' to "h",
    'ਕ' to "k", 'ਖ' to "K", 'ਗ' to "g", 'ਘ' to "G", 'ਙ' to "q",
    'ਚ' to "c", 'ਛ' to "C", 'ਜ' to "j", 'ਝ' to "J", 'ਞ' to "Q",
    'ਟ' to "t", 'ਠ' to "T", 'ਡ' to "d", 'ਢ' to "D", 'ਣ' to "x",
    'ਤ' to "q", 'ਥ' to "Q", 'ਦ' to "d", 'ਧ' to "D", 'ਨ' to "n",
    'ਪ' to "p", 'ਫ' to "P", 'ਬ' to "b", 'ਭ' to "B", 'ਮ' to "m",
    'ਯ' to "y", 'ਰ' to "r", 'ਲ' to "l", 'ਵ' to "v", 'ੜ' to "R",
    'ਸ਼' to "S", 'ਖ਼' to "X", 'ਗ਼' to "z", 'ਜ਼' to "z", 'ਫ਼' to "f", 'ਲ਼' to "L",
    'ਆ' to "A", 'ਇ' to "e", 'ਈ' to "e", 'ਉ' to "a", 'ਊ' to "a", 'ਏ' to "e", 'ਐ' to "A", 'ਓ' to "a", 'ਔ' to "a",
    'ਾ' to "w", 'ਿ' to "i", 'ੀ' to "I", 'ੁ' to "u", 'ੂ' to "U", 'ੇ' to "y", 'ੈ' to "Y", 'ੋ' to "o", 'ਔ' to "O",
    'ੰ' to "M", 'ਂ' to "M", 'ੱ' to "`", 'ੵ' to "Y"
  )
  val sb = StringBuilder()
  for (ch in input) {
    sb.append(map[ch] ?: ch)
  }
  return sb.toString()
}

fun String.toGurmukhiDigits(): String {
  if (this.isEmpty()) return this
  val sb = StringBuilder()
  for (ch in this) {
    val converted = when (ch) {
      '0' -> '੦'
      '1' -> '੧'
      '2' -> '੨'
      '3' -> '੩'
      '4' -> '੪'
      '5' -> '੫'
      '6' -> '੬'
      '7' -> '੭'
      '8' -> '੮'
      '9' -> '੯'
      else -> ch
    }
    sb.append(converted)
  }
  return sb.toString()
}

fun convertGurbaniAkharToUnicode(text: String): String {
  return com.example.util.convertGurbaniAkharToUnicode(text)
}

fun gurmukhiCharToRoman(ch: Char): Char {
  return when (ch) {
    'ੴ' -> 'i'
    'ੳ', 'ਅ', 'ੲ', 'ਆ', 'ਇ', 'ਈ', 'ਉ', 'ਊ', 'ਏ', 'ਐ', 'ਓ', 'ਔ' -> 'a'
    'ਕ', 'ਖ', 'ਖ਼', 'ਘ' -> 'k'
    'ਗ', 'ਗ਼' -> 'g'
    'ਚ', 'ਛ' -> 'c'
    'ਜ', 'ਝ', 'ਜ਼' -> 'j'
    'ਟ', 'ਠ' -> 't'
    'ਡ', 'ਢ' -> 'd'
    'ਤ', 'ਥ' -> 't'
    'ਦ', 'ਧ' -> 'd'
    'ਨ', 'ਣ' -> 'n'
    'ਪ', 'ਫ', 'ਫ਼' -> 'p'
    'ਬ', 'ਭ' -> 'b'
    'ਮ' -> 'm'
    'ਯ' -> 'y'
    'ਰ', 'ੜ' -> 'r'
    'ਲ', 'ਲ਼' -> 'l'
    'ਵ' -> 'v'
    'ਸ਼', 'ਸ' -> 's'
    'ਹ' -> 'h'
    else -> ch.lowercaseChar()
  }
}

data class WordToken(
  val word: String,
  val startIdx: Int,
  val endIdx: Int,
  val exactFirst: Char,
  val normFirst: Char,
  val romanFirst: Char
)

fun parseGurmukhiLineWords(line: String): List<WordToken> {
  val tokens = mutableListOf<WordToken>()
  var i = 0
  val len = line.length
  while (i < len) {
    while (i < len && (line[i].isWhitespace() || line[i] in "॥|,.?()0123456789੦੧੨੩੪੫੬੭੮੯-\"':;")) {
      i++
    }
    if (i >= len) break
    val start = i
    while (i < len && !(line[i].isWhitespace() || line[i] in "॥|,.?()0123456789੦੧੨੩੪੫੬੭੮੯-\"':;")) {
      i++
    }
    val word = line.substring(start, i)
    if (word.isNotEmpty()) {
      val firstChar = word[0]
      tokens.add(
        WordToken(
          word = word,
          startIdx = start,
          endIdx = i,
          exactFirst = firstChar,
          normFirst = normalizeGurmukhiChar(firstChar),
          romanFirst = gurmukhiCharToRoman(firstChar)
        )
      )
    }
  }
  return tokens
}

enum class SearchSourceTab {
  NITNEM,
  SGGS
}

@Composable
fun GurmukhiKeyboard(
  onKeyClick: (String) -> Unit,
  onBackspaceClick: () -> Unit,
  onClearClick: () -> Unit,
  onSearchClick: () -> Unit = {},
  onHideClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var keyboardTab by remember { mutableStateOf(0) } // 0: Consonants & Vowels, 1: Matras, 2: Digits & Roman
  var lastClickTime by remember { mutableStateOf(0L) }
  val debounceMs = 150L

  val handleKeyClick: (String) -> Unit = { key ->
    val now = System.currentTimeMillis()
    if (now - lastClickTime >= debounceMs) {
      lastClickTime = now
      onKeyClick(key)
    }
  }

  val handleBackspaceClick: () -> Unit = {
    val now = System.currentTimeMillis()
    if (now - lastClickTime >= debounceMs) {
      lastClickTime = now
      onBackspaceClick()
    }
  }

  val handleClearClick: () -> Unit = {
    val now = System.currentTimeMillis()
    if (now - lastClickTime >= debounceMs) {
      lastClickTime = now
      onClearClick()
    }
  }

  val handleSearchClick: () -> Unit = {
    val now = System.currentTimeMillis()
    if (now - lastClickTime >= debounceMs) {
      lastClickTime = now
      onSearchClick()
    }
  }

  val handleHideClick: () -> Unit = {
    val now = System.currentTimeMillis()
    if (now - lastClickTime >= debounceMs) {
      lastClickTime = now
      onHideClick()
    }
  }

  val consonantsAndVowels = listOf(
    listOf("ੴ", "ੳ", "ਅ", "ੲ", "ਸ", "ਹ"),
    listOf("ਕ", "ਖ", "ਗ", "ਘ", "ਙ"),
    listOf("ਚ", "ਛ", "ਜ", "ਝ", "ਞ"),
    listOf("ਟ", "ਠ", "ਡ", "ਢ", "ਣ"),
    listOf("ਤ", "ਥ", "ਦ", "ਧ", "ਨ"),
    listOf("ਪ", "ਫ", "ਬ", "ਭ", "ਮ"),
    listOf("ਯ", "ਰ", "ਲ", "ਵ", "ੜ"),
    listOf("ਸ਼", "ਖ਼", "ਗ਼", "ਜ਼", "ਫ਼", "ਲ਼")
  )

  val matrasAndAux = listOf(
    listOf("ਆ", "ਇ", "ਈ", "ਉ", "ਊ", "ਏ", "ਐ", "ਓ", "ਔ"),
    listOf("ਾ", "ਿ", "ੀ", "ੁ", "ੂ", "ੇ", "ੈ", "ੋ", "ੌ"),
    listOf("ੰ", "ਂ", "ੱ", "੍", "ੵ", "ੴ")
  )

  val digitsAndRoman = listOf(
    listOf("੦", "੧", "੨", "੩", "੪", "੫", "੬", "੭", "੮", "੯"),
    listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
    listOf("s", "a", "m", "k", "b", "i", "o", "g", "r", "p")
  )

  Card(
    modifier = modifier
      .fillMaxWidth()
      .testTag("gurmukhi_keyboard_card"),
    colors = CardDefaults.cardColors(containerColor = Slate50),
    border = BorderStroke(1.dp, Slate200),
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(4.dp)
    ) {
      // Category Tab Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        val tabs = listOf("🔤 ਅੱਖਰ (35+)", "🎨 ਮਾਤਰਾਵਾਂ", "🔢 ਅੰਕ / saskba")
        tabs.forEachIndexed { index, label ->
          val isSelected = keyboardTab == index
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .background(if (isSelected) SaffronPrimary else Color.White)
              .border(1.dp, if (isSelected) SaffronPrimary else Slate200, RoundedCornerShape(8.dp))
              .clickable { keyboardTab = index }
              .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              fontSize = 10.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) Color.White else TextMedium
            )
          }
        }
      }

      // Keyboard Key Grid
      val currentKeyRows = when (keyboardTab) {
        1 -> matrasAndAux
        2 -> digitsAndRoman
        else -> consonantsAndVowels
      }

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        currentKeyRows.forEach { rowKeys ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            rowKeys.forEach { keyStr ->
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(30.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color.White)
                  .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                  .clickable { handleKeyClick(keyStr) },
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = keyStr,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextDark,
                  textAlign = TextAlign.Center
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(3.dp))

      // Bottom Control Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        // Space key
        Box(
          modifier = Modifier
            .weight(2f)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SaffronLight)
            .border(1.dp, SaffronPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { handleKeyClick(" ") },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "␣ ਖਾਲੀ ਥਾਂ (Space)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = SaffronDark
          )
        }

        // Backspace key
        Box(
          modifier = Modifier
            .weight(1f)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Slate200, RoundedCornerShape(8.dp))
            .clickable { handleBackspaceClick() },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "⌫",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red
          )
        }

        // Clear key
        Box(
          modifier = Modifier
            .weight(1f)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Slate200, RoundedCornerShape(8.dp))
            .clickable { handleClearClick() },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "ਸਾਫ਼",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMedium
          )
        }

        // Search key
        Box(
          modifier = Modifier
            .weight(1f)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SaffronPrimary)
            .clickable { handleSearchClick() },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "🔍 ਖੋਜ",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }

        // Hide keyboard
        Box(
          modifier = Modifier
            .weight(1f)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Slate200)
            .clickable { handleHideClick() },
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "⌨️ ਓਹਲੇ",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
          )
        }
      }
    }
  }
}

@Composable
fun SearchScreen(
  settingsManager: SettingsManager? = null,
  onBack: () -> Unit,
  onNavigateToBani: (String, Int) -> Unit
) {
  val context = LocalContext.current
  val settingsState = settingsManager?.settings?.collectAsStateWithLifecycle()?.value

  var allSggsBanis by remember { mutableStateOf<List<Bani>>(emptyList()) }
  var allNitnemBanis by remember { mutableStateOf<List<Bani>>(emptyList()) }

  var activeSourceTab by remember { mutableStateOf(SearchSourceTab.SGGS) }
  var searchQuery by remember { mutableStateOf("") }
  var activeFilterTab by remember { mutableStateOf(SearchFilterType.ALL) }
  var fontOverride by remember { mutableStateOf<String?>(null) }
  var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
  var showKeyboard by remember { mutableStateOf(true) }

  val activeFontName = fontOverride ?: settingsState?.fontFamily ?: "Unicode Gurmukhi"
  val activeFontFamily = when (activeFontName) {
    "Sant Lipi" -> santLipiFontFamily
    "Unicode Gurmukhi" -> FontFamily.SansSerif
    "Noto Serif Gurmukhi" -> notoSerifGurmukhiFontFamily
    else -> notoSerifGurmukhiFontFamily
  }

  // SRI GURU GRANTH SAHIB JI BANIS (1430 Ang)
  val sggsBaniFiles = remember {
    listOf(
      "japji_sahib.json",
      "kirtan_sohila.json",
      "sri_sukhmani_sahib.json",
      "asa_di_vaar.json",
      "anand_sahib.json",
      "aarti.json"
    )
  }

  // NITNEM BANIS
  val nitnemBaniFiles = remember {
    listOf(
      "japji_sahib.json",
      "jaap_sahib.json",
      "tav_prasad_savaiye.json",
      "chaupai_sahib.json",
      "anand_sahib.json",
      "rehras_sahib.json",
      "kirtan_sohila.json",
      "ardas.json",
      "aarti.json",
      "asa_di_vaar.json",
      "sri_sukhmani_sahib.json"
    )
  }

  // Instant real-time offline search (1430 Angs Sri Guru Granth Sahib Ji & Nitnem Banis)
  LaunchedEffect(searchQuery, activeFilterTab, activeSourceTab) {
    val q = searchQuery.trim()
    if (q.isBlank()) {
      searchResults = emptyList()
      return@LaunchedEffect
    }

    kotlinx.coroutines.delay(200)

    withContext(Dispatchers.IO) {
      val results = mutableListOf<SearchResult>()
      val cleanQ = q.replace(" ", "").replace("॥", "").replace("|", "")
      val cleanLowerQ = cleanQ.lowercase()
      val cleanNormQ = cleanQ.map { normalizeGurmukhiChar(it) }.joinToString("")
      val isRomanQuery = cleanLowerQ.any { it in 'a'..'z' }
      val parsedAng = q.replace("ਅੰਗ", "").replace("ang", "", ignoreCase = true).trim().toIntOrNull()

      try {
        val sggsDb = SggsDatabase.getInstance(context)

        if (activeSourceTab == SearchSourceTab.NITNEM) {
          val nitnemResults = mutableListOf<SearchResult>()
          nitnemBaniFiles.forEach { fn ->
            val bani = loadBaniFromAsset(context, fn)
            bani.verses.forEach { verse ->
              val lineText = verse.line
              val cleanLine = lineText.replace("॥", "").replace("।", "").replace("|", "").trim()
              val firstLetters = cleanLine.split("\\s+".toRegex()).mapNotNull { word ->
                word.firstOrNull { char -> char.isLetter() || char in '\u0A00'..'\u0A7F' }
              }.joinToString("")

              val matches = when (activeFilterTab) {
                SearchFilterType.FIRST_LETTER -> firstLetters.startsWith(cleanQ, ignoreCase = true) || firstLetters.startsWith(cleanNormQ, ignoreCase = true)
                SearchFilterType.FULL_TEXT -> lineText.contains(q, ignoreCase = true) || cleanLine.replace(" ", "").contains(cleanQ, ignoreCase = true)
                SearchFilterType.ANG -> false
                else -> firstLetters.startsWith(cleanQ, ignoreCase = true) || lineText.contains(q, ignoreCase = true) || cleanLine.replace(" ", "").contains(cleanQ, ignoreCase = true)
              }

              if (matches) {
                nitnemResults.add(
                  SearchResult(
                    baniName = bani.title,
                    fileName = fn,
                    verse = verse,
                    searchMethod = activeFilterTab,
                    highlightRange = null,
                    matchedQuery = q,
                    ang = 0
                  )
                )
              }
            }
          }
          results.addAll(nitnemResults)
        } else {
          val rawItems = mutableListOf<com.example.data.LineWithTranslation>()

          if (parsedAng != null && (activeFilterTab == SearchFilterType.ALL || activeFilterTab == SearchFilterType.ANG)) {
            rawItems.addAll(sggsDb.searchByAng(parsedAng))
          }

          if (cleanQ.isNotEmpty()) {
            val asciiQ = convertGurmukhiToGurbaniAkharAscii(cleanQ)
            val fullTextAsciiQ = convertGurmukhiToGurbaniAkharAscii(q.trim())
            if (activeFilterTab == SearchFilterType.ALL || activeFilterTab == SearchFilterType.FIRST_LETTER) {
              rawItems.addAll(sggsDb.searchByFirstLetters(cleanQ, asciiQ))
            }
            if (activeFilterTab == SearchFilterType.ALL || activeFilterTab == SearchFilterType.FULL_TEXT) {
              rawItems.addAll(sggsDb.searchByFullText(q.trim(), fullTextAsciiQ))
            }
          }

          val dbLines = rawItems.map { item ->
            item.line.apply {
              translation = item.translation ?: ""
              punjabiTranslation = item.punjabiTranslation ?: ""
            }
          }

          val uniqueLines = dbLines.distinctBy { it.id }
          val asciiQ = convertGurmukhiToGurbaniAkharAscii(cleanQ)

          uniqueLines.forEach { lineEntity ->
            val idxOnAng = (lineEntity.source_line ?: 1) - 1

            val verse = Verse(
              id = lineEntity.id.toIntOrNull() ?: lineEntity.id.hashCode(),
              index = if (idxOnAng >= 0) idxOnAng else 0,
              line = convertGurbaniAkharToUnicode(lineEntity.gurmukhi),
              translation = lineEntity.translation,
              punjabiTranslation = lineEntity.punjabiTranslation
            )

            val raagSuffix = if (lineEntity.raag.isNotEmpty()) " • ${lineEntity.raag}" else ""
            val titleText = "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ (ਅੰਗ ${lineEntity.source_page})$raagSuffix"

            val fl = lineEntity.first_letters ?: ""
            val filterType = when {
              parsedAng != null && lineEntity.source_page == parsedAng -> SearchFilterType.ANG
              cleanQ.isNotEmpty() && (fl.startsWith(cleanQ, ignoreCase = true) || fl.startsWith(asciiQ, ignoreCase = true)) -> SearchFilterType.FIRST_LETTER
              else -> SearchFilterType.FULL_TEXT
            }

            results.add(
              SearchResult(
                baniName = titleText,
                fileName = "sggs_shabad_${lineEntity.shabad_id}",
                verse = verse,
                searchMethod = filterType,
                highlightRange = null,
                matchedQuery = q,
                ang = lineEntity.source_page
              )
            )
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }

      withContext(Dispatchers.Main) {
        searchResults = results
      }
    }
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("search_screen_scaffold"),
    containerColor = Color.White
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp)
    ) {
      TopAppBar(
        title = "🔍 ਗੁਰਬਾਣੀ ਖੋਜ (Gurbani Search)",
        onBack = onBack,
        modifier = Modifier.padding(top = 4.dp)
      )

      // Header Badge: Complete Sri Guru Granth Sahib Ji (1430 Ang)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .background(SaffronLight, RoundedCornerShape(12.dp))
          .border(1.dp, SaffronPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "ੴ ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ (1430 ਅੰਗ)",
            style = MaterialTheme.typography.bodySmall.copy(
              color = SaffronDark,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
          )
          Text(
            text = "ਆਫਲਾਈਨ • 100% Offline",
            style = MaterialTheme.typography.bodySmall.copy(
              color = TextGray,
              fontSize = 11.sp
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Search Input Row with Keyboard Toggle and Search Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = {
            Text(
              text = "ਅੱਖਰ, ਸ਼ਬਦ (ਜਿਵੇਂ 'ਸਅਸਮ', 'saskba', 'ਸਤਿਗੁਰ')...",
              color = TextGray,
              fontSize = 13.sp
            )
          },
          singleLine = true,
          keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
          ),
          keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { showKeyboard = false }
          ),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = Slate50,
            unfocusedContainerColor = Slate50,
            focusedTextColor = TextMedium,
            unfocusedTextColor = TextMedium,
            focusedIndicatorColor = SaffronPrimary,
            unfocusedIndicatorColor = Slate200,
            cursorColor = SaffronPrimary
          ),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("search_input_field")
        )

        // Visible Search Button
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SaffronPrimary)
            .clickable { showKeyboard = false }
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag("search_submit_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "🔍 ਖੋਜ",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
        }

        // Toggle Gurmukhi Keyboard Button
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (showKeyboard) SaffronPrimary else Slate100)
            .border(1.dp, if (showKeyboard) SaffronPrimary else Slate200, RoundedCornerShape(14.dp))
            .clickable { showKeyboard = !showKeyboard }
            .padding(horizontal = 10.dp, vertical = 14.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = if (showKeyboard) "⌨️" else "⌨️+ ",
            fontSize = 16.sp,
            color = if (showKeyboard) Color.White else TextMedium
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Interactive Method Filter Tabs
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        val filterTabs = listOf(
          SearchFilterType.ALL to "🔍 ਸਭ",
          SearchFilterType.FIRST_LETTER to "🔤 ਪਹਿਲਾ ਅੱਖਰ",
          SearchFilterType.FULL_TEXT to "📝 ਪੂਰਾ ਪਾਠ",
          SearchFilterType.ANG to "📖 ਅੰਗ"
        )

        filterTabs.forEach { (type, label) ->
          val isSelected = activeFilterTab == type
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) SaffronPrimary else Slate100)
              .clickable { activeFilterTab = type }
              .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = label,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) Color.White else TextMedium,
              textAlign = TextAlign.Center
            )
          }
        }
      }

      // Font Toggle Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "ਫੋਂਟ: $activeFontName",
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = TextMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (activeFontName == "Unicode Gurmukhi") SaffronLight else Slate100)
              .border(
                1.dp,
                if (activeFontName == "Unicode Gurmukhi") SaffronPrimary else Color.Transparent,
                RoundedCornerShape(8.dp)
              )
              .clickable { fontOverride = "Unicode Gurmukhi" }
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Text(
              text = "🔤 ਯੂਨੀਕੋਡ",
              fontSize = 10.sp,
              color = if (activeFontName == "Unicode Gurmukhi") SaffronDark else TextMedium,
              fontWeight = FontWeight.Bold
            )
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (activeFontName == "Sant Lipi") SaffronLight else Slate100)
              .border(
                1.dp,
                if (activeFontName == "Sant Lipi") SaffronPrimary else Color.Transparent,
                RoundedCornerShape(8.dp)
              )
              .clickable { fontOverride = "Sant Lipi" }
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Text(
              text = "📜 ਸੰਤ ਲਿਪੀ",
              fontSize = 10.sp,
              color = if (activeFontName == "Sant Lipi") SaffronDark else TextMedium,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      // Main Content Area: Search Results or Hints
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) {
        if (searchQuery.isBlank()) {
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "ੴ",
              style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 36.sp,
                color = SaffronPrimary.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
              ),
              modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
              text = if (activeSourceTab == SearchSourceTab.SGGS) "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ ਆਫਲਾਈਨ ਖੋਜ" else "ਨਿਤਨੇਮ ਬਾਣੀਆਂ ਆਫਲਾਈਨ ਖੋਜ",
              style = MaterialTheme.typography.titleMedium.copy(
                color = TextDark,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "• 🔤 ਪਹਿਲਾ ਅੱਖਰ: 'ਸਅਸਮ' ਜਾਂ 'saskba'\n• 📝 ਪੂਰਾ ਪਾਠ: 'ਸਤਿਗੁਰ' ਜਾਂ 'ਅਰਦਾਸਿ'\n• 📖 ਅੰਗ ਨੰਬਰ: '262', '1', '917'",
              style = MaterialTheme.typography.bodyMedium.copy(
                color = TextGray,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
              ),
              modifier = Modifier.testTag("search_empty_hint")
            )
          }
        } else if (searchResults.isEmpty()) {
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "ਕੋਈ ਨਤੀਜਾ ਨਹੀਂ ਮਿਲਿਆ",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextMedium,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
              ),
              modifier = Modifier.testTag("search_no_results_text")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "ਕਿਰਪਾ ਕਰਕੇ ਹੋਰ ਅੱਖਰ, ਸ਼ਬਦ ਜਾਂ ਅੰਗ ਨੰਬਰ ਲਿਖੋ।",
              style = MaterialTheme.typography.bodyMedium.copy(
                color = TextGray,
                fontSize = 12.sp
              )
            )
          }
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            Text(
              text = "${searchResults.size} ਨਤੀਜੇ ਮਿਲੇ (${if (activeSourceTab == SearchSourceTab.SGGS) "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ" else "ਨਿਤਨੇਮ"})",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = SaffronDark,
              modifier = Modifier.padding(bottom = 6.dp)
            )

            LazyColumn(
              modifier = Modifier
                .fillMaxSize()
                .testTag("search_results_list"),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
            ) {
              itemsIndexed(
                items = searchResults,
                key = { index, result -> "${result.fileName}_${result.verse.id}_${result.verse.index}_$index" }
              ) { index, result ->
                Card(
                  onClick = {
                    val navTarget = if (result.fileName.isNotBlank()) result.fileName else result.baniName
                    onNavigateToBani(navTarget, result.verse.index)
                  },
                  colors = CardDefaults.cardColors(containerColor = Slate50),
                  border = BorderStroke(1.dp, Slate200),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_result_card_$index")
                ) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(12.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Box(
                          modifier = Modifier
                            .background(SaffronLight, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                          Text(
                            text = result.baniName,
                            style = MaterialTheme.typography.bodySmall.copy(
                              color = SaffronDark,
                              fontWeight = FontWeight.Bold,
                              fontSize = 11.sp
                            ),
                            modifier = Modifier.testTag("search_result_bani_name_$index")
                          )
                        }

                        Box(
                          modifier = Modifier
                            .background(Slate200, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                          Text(
                            text = "📖 ਅੰਗ ${result.ang}",
                            style = MaterialTheme.typography.bodySmall.copy(
                              color = TextMedium,
                              fontWeight = FontWeight.Bold,
                              fontSize = 11.sp
                            )
                          )
                        }
                      }

                      val methodLabel = when (result.searchMethod) {
                        SearchFilterType.FIRST_LETTER -> "🔤 ਪਹਿਲਾ ਅੱਖਰ"
                        SearchFilterType.ANG -> "📖 ਅੰਗ"
                        else -> "📝 ਪੂਰਾ ਪਾਠ"
                      }
                      Text(
                        text = methodLabel,
                        fontSize = 10.sp,
                        color = TextGray,
                        fontWeight = FontWeight.SemiBold
                      )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    HighlightedText(
                      text = result.verse.line,
                      query = result.matchedQuery,
                      highlightRange = result.highlightRange,
                      highlightColor = SaffronPrimary,
                      textColor = TextMedium,
                      fontSize = 17.sp,
                      fontWeight = FontWeight.Bold,
                      fontFamily = activeFontFamily
                    )

                    if (result.verse.translation.isNotEmpty() && settingsState?.showTranslation != false) {
                      Spacer(modifier = Modifier.height(6.dp))
                      HighlightedText(
                        text = result.verse.translation,
                        query = result.matchedQuery,
                        highlightColor = SaffronPrimary.copy(alpha = 0.8f),
                        textColor = TextGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                      )
                    }

                    if (result.verse.punjabiTranslation.isNotEmpty() && settingsState?.showPunjabiTranslation == true) {
                      Spacer(modifier = Modifier.height(6.dp))
                      HighlightedText(
                        text = result.verse.punjabiTranslation,
                        query = result.matchedQuery,
                        highlightColor = SaffronPrimary.copy(alpha = 0.8f),
                        textColor = TextGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // BUILT-IN CUSTOM GURMUKHI KEYBOARD AT THE BOTTOM
      if (showKeyboard) {
        GurmukhiKeyboard(
          onKeyClick = { key -> searchQuery += key },
          onBackspaceClick = {
            if (searchQuery.isNotEmpty()) {
              searchQuery = searchQuery.dropLast(1)
            }
          },
          onClearClick = { searchQuery = "" },
          onSearchClick = { showKeyboard = false },
          onHideClick = { showKeyboard = false },
          modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
        )
      }
    }
  }
}

@Composable
fun HighlightedText(
  text: String,
  query: String = "",
  highlightRange: IntRange? = null,
  highlightColor: Color = SaffronPrimary,
  textColor: Color = TextDark,
  fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
  fontWeight: FontWeight = FontWeight.Normal,
  fontFamily: FontFamily = FontFamily.Default,
  modifier: Modifier = Modifier
) {
  val annotatedString = remember(text, query, highlightRange) {
    buildAnnotatedString {
      if (highlightRange != null && highlightRange.first >= 0 && highlightRange.last < text.length) {
        if (highlightRange.first > 0) {
          append(text.substring(0, highlightRange.first))
        }
        withStyle(style = SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
          append(text.substring(highlightRange.first, highlightRange.last + 1))
        }
        if (highlightRange.last + 1 < text.length) {
          append(text.substring(highlightRange.last + 1))
        }
      } else if (query.isNotEmpty() && text.contains(query, ignoreCase = true)) {
        var startIndex = 0
        while (true) {
          val index = text.indexOf(query, startIndex, ignoreCase = true)
          if (index == -1) {
            append(text.substring(startIndex))
            break
          }
          append(text.substring(startIndex, index))
          withStyle(style = SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(text.substring(index, index + query.length))
          }
          startIndex = index + query.length
        }
      } else {
        append(text)
      }
    }
  }

  Text(
    text = annotatedString,
    fontSize = fontSize,
    color = textColor,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    modifier = modifier
  )
}

@Composable
fun BookmarksScreen(
  viewModel: BookmarksViewModel,
  onBack: () -> Unit,
  onNavigateToBani: (String, Int) -> Unit
) {
  val bookmarks by viewModel.allBookmarks.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var showClearConfirmDialog by remember { mutableStateOf(false) }

  val resolvedMap = remember { mutableStateMapOf<Int, com.example.util.ResolvedBookmarkDisplay>() }

  LaunchedEffect(bookmarks) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      bookmarks.forEach { b ->
        if (!resolvedMap.containsKey(b.id)) {
          val res = com.example.util.GurbaniUtils.resolveBookmarkDisplay(b, context)
          kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            resolvedMap[b.id] = res
          }
        }
      }
    }
  }

  val filteredBookmarks = remember(bookmarks, searchQuery, resolvedMap.toMap()) {
    if (searchQuery.isBlank()) {
      bookmarks
    } else {
      bookmarks.filter { b ->
        val resolved = resolvedMap[b.id] ?: com.example.util.GurbaniUtils.resolveBookmarkDisplayFast(b)
        b.verseLine.contains(searchQuery, ignoreCase = true) ||
        b.translation.contains(searchQuery, ignoreCase = true) ||
        resolved.title.contains(searchQuery, ignoreCase = true) ||
        (resolved.subtitle != null && resolved.subtitle.contains(searchQuery, ignoreCase = true)) ||
        resolved.badgeText.contains(searchQuery, ignoreCase = true)
      }
    }
  }

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json")
  ) { uri ->
    if (uri != null) {
      try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
          val jsonArray = org.json.JSONArray()
          bookmarks.forEach { b ->
            val obj = org.json.JSONObject()
            obj.put("baniName", b.baniName)
            obj.put("fileName", b.fileName)
            obj.put("lineIndex", b.lineIndex)
            obj.put("verseLine", b.verseLine)
            obj.put("translation", b.translation)
            obj.put("timestamp", b.timestamp)
            jsonArray.put(obj)
          }
          outputStream.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
        }
        Toast.makeText(context, "ਬੁੱਕਮਾਰਕ ਨਿਰਯਾਤ (Export) ਹੋ ਗਏ॥", Toast.LENGTH_LONG).show()
      } catch (e: Exception) {
        Toast.makeText(context, "Export ਵਿੱਚ ਗਲਤੀ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
    }
  }

  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri != null) {
      try {
        val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        if (!jsonString.isNullOrEmpty()) {
          val jsonArray = org.json.JSONArray(jsonString)
          val importedList = mutableListOf<Bookmark>()
          for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            importedList.add(
              Bookmark(
                baniName = obj.optString("baniName", ""),
                fileName = obj.optString("fileName", ""),
                lineIndex = obj.optInt("lineIndex", 0),
                verseLine = obj.optString("verseLine", ""),
                translation = obj.optString("translation", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
              )
            )
          }
          if (importedList.isNotEmpty()) {
            viewModel.importBookmarks(importedList) { count ->
              Toast.makeText(context, "$count ਬੁੱਕਮਾਰਕ ਆਯਾਤ (Import) ਹੋ ਗਏ॥", Toast.LENGTH_LONG).show()
            }
          } else {
            Toast.makeText(context, "ਫਾਈਲ ਵਿੱਚ ਕੋਈ ਬੁੱਕਮਾਰਕ ਨਹੀਂ ਮਿਲਿਆ।", Toast.LENGTH_SHORT).show()
          }
        }
      } catch (e: Exception) {
        Toast.makeText(context, "Import ਵਿੱਚ ਗਲਤੀ: JSON ਫਾਈਲ ਦਾ ਫਾਰਮੈਟ ਸਹੀ ਨਹੀਂ ਹੈ।", Toast.LENGTH_LONG).show()
      }
    }
  }

  if (showClearConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showClearConfirmDialog = false },
      title = { Text("ਬੁੱਕਮਾਰਕ ਹਟਾਓ", fontWeight = FontWeight.Bold) },
      text = { Text("ਕੀ ਤੁਸੀਂ ਸਾਰੇ ਬੁੱਕਮਾਰਕ ਸਾਫ਼ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.clearAllBookmarks {
              Toast.makeText(context, "ਸਾਰੇ ਬੁੱਕਮਾਰਕ ਸਾਫ਼ ਹੋ ਗਏ॥", Toast.LENGTH_SHORT).show()
            }
            showClearConfirmDialog = false
          },
          modifier = Modifier.testTag("confirm_clear_bookmarks")
        ) {
          Text("ਹਾਂ, ਸਾਫ਼ ਕਰੋ", color = Color.Red, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirmDialog = false }) {
          Text("ਰੱਦ ਕਰੋ")
        }
      }
    )
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("bookmarks_screen_scaffold"),
    containerColor = Color.White
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp)
    ) {
      TopAppBar(
        title = "⭐ ਬੁੱਕਮਾਰਕਸ",
        onBack = onBack,
        modifier = Modifier.padding(top = 8.dp)
      )

      Spacer(modifier = Modifier.height(8.dp))

      // Action buttons row: Export, Import, Clear
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Card(
          onClick = {
            if (bookmarks.isEmpty()) {
              Toast.makeText(context, "ਨਿਰਯਾਤ ਕਰਨ ਲਈ ਕੋਈ ਬੁੱਕਮਾਰਕ ਨਹੀਂ ਹੈ।", Toast.LENGTH_SHORT).show()
            } else {
              exportLauncher.launch("gurbani_bookmarks_${System.currentTimeMillis()}.json")
            }
          },
          colors = CardDefaults.cardColors(containerColor = SaffronLight),
          border = BorderStroke(1.dp, SaffronPrimary),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(40.dp).testTag("export_bookmarks_btn")
        ) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("📤 Export", color = SaffronDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }

        Card(
          onClick = { importLauncher.launch("application/json") },
          colors = CardDefaults.cardColors(containerColor = Slate50),
          border = BorderStroke(1.dp, Slate200),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f).height(40.dp).testTag("import_bookmarks_btn")
        ) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("📥 Import", color = TextMedium, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }

        if (bookmarks.isNotEmpty()) {
          Card(
            onClick = { showClearConfirmDialog = true },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(40.dp).testTag("clear_bookmarks_btn")
          ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("🗑️ ਸਾਫ਼ ਕਰੋ", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (bookmarks.isNotEmpty()) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("ਬੁੱਕਮਾਰਕ ਖੋਜੋ...", color = TextGray, fontSize = 14.sp) },
          leadingIcon = { Text("🔍", fontSize = 14.sp) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Text("✕", fontSize = 12.sp, color = TextGray)
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors = TextFieldDefaults.colors(
            focusedContainerColor = Slate50,
            unfocusedContainerColor = Slate50,
            focusedIndicatorColor = SaffronPrimary,
            unfocusedIndicatorColor = Slate200
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("bookmarks_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))
      }

      if (bookmarks.isEmpty()) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "☆",
            style = MaterialTheme.typography.displayMedium.copy(
              fontSize = 48.sp,
              color = SaffronPrimary.copy(alpha = 0.5f),
              fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 12.dp)
          )
          Text(
            text = "ਕੋਈ ਬੁੱਕਮਾਰਕ ਨਹੀਂ ਮਿਲਿਆ।\nਪਾਠ ਕਰਦੇ ਸਮੇਂ ਲਾਈਨ ਦੇ ਨਾਲ ਦਿੱਤੇ ⭐ ਆਈਕਨ 'ਤੇ ਟੈਪ ਕਰਕੇ ਬੁੱਕਮਾਰਕ ਕਰੋ।\nਜਾਂ 'Import' ਰਾਹੀਂ ਬੁੱਕਮਾਰਕ ਫਾਈਲ ਸ਼ਾਮਲ ਕਰੋ।",
            style = MaterialTheme.typography.bodyMedium.copy(
              color = TextGray,
              fontSize = 14.sp,
              textAlign = TextAlign.Center,
              lineHeight = 22.sp
            ),
            modifier = Modifier.testTag("bookmarks_empty_hint")
          )
        }
      } else if (filteredBookmarks.isEmpty()) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "'$searchQuery' ਲਈ ਕੋਈ ਬੁੱਕਮਾਰਕ ਨਹੀਂ ਮਿਲਿਆ।",
            style = MaterialTheme.typography.bodyMedium.copy(
              color = TextGray,
              fontSize = 14.sp,
              textAlign = TextAlign.Center
            ),
            modifier = Modifier.testTag("bookmarks_no_search_results")
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .weight(1f)
            .testTag("bookmarks_list"),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
          itemsIndexed(filteredBookmarks, key = { _, b -> "${b.fileName}_${b.lineIndex}_${b.id}" }) { index, bookmark ->
            val resolved = resolvedMap[bookmark.id] ?: com.example.util.GurbaniUtils.resolveBookmarkDisplayFast(bookmark)

            Card(
              onClick = {
                val navTarget = if (bookmark.fileName.isNotBlank() && (bookmark.fileName.startsWith("sggs_") || bookmark.fileName.endsWith(".json"))) bookmark.fileName else bookmark.baniName
                onNavigateToBani(navTarget, bookmark.lineIndex)
              },
              colors = CardDefaults.cardColors(containerColor = Slate50),
              border = BorderStroke(1.dp, Slate200),
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("bookmark_card_$index")
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .background(SaffronLight, shape = RoundedCornerShape(8.dp))
                      .padding(horizontal = 10.dp, vertical = 4.dp)
                  ) {
                    Text(
                      text = resolved.badgeText,
                      style = MaterialTheme.typography.bodySmall.copy(
                        color = SaffronDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                      ),
                      modifier = Modifier.testTag("bookmark_bani_name_$index")
                    )
                  }

                  IconButton(
                    onClick = {
                      viewModel.toggleBookmark(
                        baniName = bookmark.baniName,
                        fileName = bookmark.fileName,
                        lineIndex = bookmark.lineIndex,
                        verseLine = bookmark.verseLine,
                        translation = bookmark.translation
                      ) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                      }
                    },
                    modifier = Modifier.size(28.dp).testTag("delete_bookmark_$index")
                  ) {
                    Text(text = "❌", fontSize = 12.sp)
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title (Mukhvak for SGGS, or Bani Name for Nitnem / Other)
                HighlightedText(
                  text = resolved.title,
                  query = searchQuery,
                  textColor = SaffronDark,
                  highlightColor = SaffronPrimary,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag("bookmark_title_$index")
                )

                if (resolved.subtitle != null) {
                  Spacer(modifier = Modifier.height(2.dp))
                  HighlightedText(
                    text = resolved.subtitle,
                    query = searchQuery,
                    textColor = TextGray,
                    highlightColor = SaffronPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("bookmark_subtitle_$index")
                  )
                }

                // If bookmarked verse line is distinct from the title (e.g. specific verse inside a shabad), show it
                if (bookmark.verseLine.isNotBlank() && bookmark.verseLine.trim() != resolved.title.trim()) {
                  Spacer(modifier = Modifier.height(8.dp))
                  HighlightedText(
                    text = bookmark.verseLine,
                    query = searchQuery,
                    textColor = TextMedium,
                    highlightColor = SaffronPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("bookmark_verse_$index")
                  )
                }

                if (bookmark.translation.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(6.dp))
                  HighlightedText(
                    text = bookmark.translation,
                    query = searchQuery,
                    textColor = TextGray,
                    highlightColor = SaffronPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                  )
                }
              }
            }
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .width(128.dp)
            .height(6.dp)
            .background(color = Slate200, shape = RoundedCornerShape(3.dp))
        )
      }
    }
  }
}

@Composable
fun WelcomeScreen(
  onGetStarted: () -> Unit
) {
  var visible by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    visible = true
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("welcome_screen_scaffold"),
    containerColor = Color.White
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 24.dp)
      ) {
        AnimatedVisibility(
          visible = visible,
          enter = fadeIn(animationSpec = spring()) + slideInVertically(initialOffsetY = { -40 }, animationSpec = spring())
        ) {
          Text(
            text = "ੴ",
            style = MaterialTheme.typography.displayLarge.copy(
              fontSize = 80.sp,
              color = SaffronPrimary,
              fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.testTag("welcome_ek_onkar")
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedVisibility(
          visible = visible,
          enter = fadeIn(animationSpec = spring()) + slideInVertically(initialOffsetY = { 40 }, animationSpec = spring())
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "ਜੀ ਆਇਆਂ ਨੂੰ - ਗੁਰਬਾਣੀ ਖੋਜ",
              style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = TextMedium,
                textAlign = TextAlign.Center
              ),
              modifier = Modifier.testTag("welcome_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ ਅਤੇ ਨਿਤਨੇਮ ਬਾਣੀਆਂ ਦਾ ਸਰਲ ਸੰਚਾਰ",
              style = MaterialTheme.typography.bodyMedium.copy(
                color = TextGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
              ),
              modifier = Modifier.testTag("welcome_subtitle")
            )
          }
        }
      }

      AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(stiffness = 100f)) + slideInVertically(initialOffsetY = { 60 }, animationSpec = spring(stiffness = 100f)),
        modifier = Modifier.weight(1f).padding(vertical = 16.dp)
      ) {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
          item {
            WelcomeFeatureRow(
              emoji = "📖",
              title = "ਸੰਪੂਰਨ ਨਿਤਨੇਮ ਬਾਣੀਆਂ",
              desc = "ਜਪੁਜੀ ਸਾਹਿਬ, ਜਾਪੁ ਸਾਹਿਬ, ਚੌਪਈ ਸਾਹਿਬ, ਅਨੰਦ ਸਾਹਿਬ, ਰਹਿਰਾਸ ਸਾਹਿਬ ਤੇ ਹੋਰ ਸਾਰੀਆਂ ਪਾਵਨ ਬਾਣੀਆਂ।"
            )
          }
          item {
            WelcomeFeatureRow(
              emoji = "🔍",
              title = "ਤੀਵਰ ਗੁਰਬਾਣੀ ਖੋਜ",
              desc = "ਪਹਿਲੇ ਅੱਖਰ ਨਾਲ ਜਾਂ ਪੂਰੇ ਸ਼ਬਦ ਨਾਲ ਆਫਲਾਈਨ ਖੋਜ ਕਰੋ।"
            )
          }
          item {
            WelcomeFeatureRow(
              emoji = "🎨",
              title = "ਅਨੁਕੂਲ ਪਾਠ ਅਨੁਭਵ",
              desc = "ਫੋਂਟ ਅਕਾਰ, ਪੁਰਾਤਨ ਸੁੰਦਰ ਲਿਪੀਆਂ (ਸੰਤ ਲਿਪੀ), ਲਾਈਨਾਂ ਦੀ ਵਿੱਥ ਅਤੇ ਵਿਸ਼ਰਾਮ ਹਾਈਲਾਈਟ।"
            )
          }
          item {
            WelcomeFeatureRow(
              emoji = "💾",
              title = "ਬੁੱਕਮਾਰਕਸ ਤੇ ਬੈਕਅੱਪ",
              desc = "100% ਆਫਲਾਈਨ। ਆਪਣੇ ਮਨਪਸੰਦ ਸ਼ਬਦ ਸੇਵ ਕਰੋ ਅਤੇ JSON ਰਾਹੀਂ Export/Import ਕਰੋ।"
            )
          }
        }
      }

      Card(
        onClick = onGetStarted,
        colors = CardDefaults.cardColors(containerColor = SaffronPrimary),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
          .testTag("get_started_btn")
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = "ਸ਼ੁਰੂ ਕਰੋ (Get Started)",
            style = MaterialTheme.typography.titleMedium.copy(
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp
            )
          )
        }
      }
    }
  }
}

@Composable
fun WelcomeFeatureRow(emoji: String, title: String, desc: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = Slate50),
    border = BorderStroke(1.dp, Slate200),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(text = emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
      Column {
        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMedium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = desc, fontSize = 12.sp, color = TextGray, lineHeight = 16.sp)
      }
    }
  }
}

@Composable
fun AboutScreen(
  onBack: () -> Unit
) {
  val versionName = remember {
    try {
      com.example.BuildConfig.VERSION_NAME
    } catch (e: Exception) {
      "1.0.0"
    }
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("about_screen_scaffold"),
    containerColor = Color.White
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp)
    ) {
      TopAppBar(
        title = "ਐਪ ਬਾਰੇ",
        onBack = onBack,
        modifier = Modifier.padding(top = 8.dp)
      )

      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f)
          .testTag("about_content_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
      ) {
        // App Header Card
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = SaffronLight),
            border = BorderStroke(1.dp, SaffronBorder),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("about_header_card")
          ) {
            Column(
              modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
text = "ਵਾਹਿਗੁਰੂ",
                style = MaterialTheme.typography.displayMedium.copy(
                  fontSize = 52.sp,
                  color = SaffronPrimary,
                  fontWeight = FontWeight.Bold
                )
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Gurbani Khoj",
                style = MaterialTheme.typography.headlineMedium.copy(
                  fontWeight = FontWeight.ExtraBold,
                  color = TextMedium,
                  fontSize = 24.sp
                ),
                modifier = Modifier.testTag("about_app_name")
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "ਗੁਰਬਾਣੀ ਖੋਜ",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.Bold,
                  color = SaffronDark,
                  fontSize = 16.sp
                )
              )
              Spacer(modifier = Modifier.height(8.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(SaffronPrimary)
                  .padding(horizontal = 12.dp, vertical = 4.dp)
              ) {
                Text(
                  text = "Version $versionName",
                  style = MaterialTheme.typography.labelMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  ),
                  modifier = Modifier.testTag("about_app_version")
                )
              }
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "Made by",
                style = MaterialTheme.typography.labelLarge.copy(
                  color = TextGray,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium
                )
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Manjot Singh M.Aa✶",
                style = MaterialTheme.typography.titleMedium.copy(
                  color = TextMedium,
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp
                ),
                modifier = Modifier.testTag("made_by_credit")
              )
            }
          }
        }

        // About Gurbani Khoj Section Card
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = Slate50),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("about_details_card")
          ) {
            Column(
              modifier = Modifier.padding(20.dp)
            ) {
              Text(
                text = "ਐਪ ਬਾਰੇ",
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  color = TextMedium,
                  fontSize = 18.sp
                ),
                modifier = Modifier.testTag("about_info_title")
              )
              Spacer(modifier = Modifier.height(12.dp))

              val bulletPoints = listOf(
                "ਇਹ ਐਪ 100% offline ਹੈ।",
                "ਗੁਰਬਾਣੀ ਖੋਜ ਇੱਕ ਮੁਫ਼ਤ ਅਤੇ ਆਫਲਾਈਨ ਗੁਰਬਾਣੀ ਖੋਜ ਐਪ ਹੈ।",
                "ਇਸ ਐਪ ਰਾਹੀਂ ਸ੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ ਵਿੱਚੋਂ ਗੁਰਬਾਣੀ ਨੂੰ ਤੇਜ਼ੀ ਨਾਲ ਖੋਜਿਆ ਜਾ ਸਕਦਾ ਹੈ।",
                "ਅੱਖਰ ਖੋਜ ਅਤੇ ਪੂਰੇ ਸ਼ਬਦਾਂ ਰਾਹੀਂ ਖੋਜ ਦੀ ਸੁਵਿਧਾ ਉਪਲਬਧ ਹੈ।",
                "ਨਿਤਨੇਮ ਬਾਣੀਆਂ ਨੂੰ ਸੌਖੇ ਅਤੇ ਸਾਫ਼ ਇੰਟਰਫੇਸ ਵਿੱਚ ਪੜ੍ਹਿਆ ਜਾ ਸਕਦਾ ਹੈ।",
                "ਮਨਪਸੰਦ ਗੁਰਬਾਣੀ ਨੂੰ ਬੁੱਕਮਾਰਕ ਕਰਕੇ ਬਾਅਦ ਵਿੱਚ ਆਸਾਨੀ ਨਾਲ ਪੜ੍ਹਿਆ ਜਾ ਸਕਦਾ ਹੈ।",
                "ਇਹ ਐਪ ਸੰਗਤ ਦੀ ਸੇਵਾ ਲਈ ਸਤਿਕਾਰ ਅਤੇ ਸਾਦਗੀ ਨਾਲ ਤਿਆਰ ਕੀਤੀ ਗਈ ਹੈ।",
                "ਸਾਡਾ ਉਦੇਸ਼ ਗੁਰਬਾਣੀ ਨੂੰ ਹਰ ਕਿਸੇ ਤੱਕ ਆਸਾਨੀ ਨਾਲ ਪਹੁੰਚਾਉਣਾ ਹੈ।"
              )

              bulletPoints.forEach { point ->
                Row(
                  modifier = Modifier.padding(vertical = 4.dp),
                  verticalAlignment = Alignment.Top
                ) {
                  Text(
                    text = "• ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontWeight = FontWeight.Bold,
                      color = SaffronPrimary,
                      fontSize = 15.sp
                    )
                  )
                  Text(
                    text = point,
                    style = MaterialTheme.typography.bodyMedium.copy(
                      color = TextMedium,
                      fontSize = 14.sp,
                      lineHeight = 20.sp
                    )
                  )
                }
              }
            }
          }
        }

        // Informational Note Card
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = Slate50),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("about_note_card")
          ) {
            Column(
              modifier = Modifier.padding(16.dp)
            ) {
              Text(
                text = "ਨੋਟ: ਜਪੁਜੀ ਸਾਹਿਬ, ਅਨੰਦ ਸਾਹਿਬ, ਸੁਖਮਨੀ ਸਾਹਿਬ ਅਤੇ ਹੋਰ ਗੁਰਬਾਣੀ ਦੀ ਵਿਆਖਿਆ \"ਸ਼੍ਰੀ ਗੁਰੂ ਗ੍ਰੰਥ ਸਾਹਿਬ ਜੀ\" ਭਾਗ ਵਿੱਚ ਪੜ੍ਹੀ ਜਾ ਸਕਦੀ ਹੈ।",
                style = MaterialTheme.typography.bodyMedium.copy(
                  color = TextMedium,
                  fontSize = 14.sp,
                  lineHeight = 21.sp,
                  fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.testTag("about_note_text")
              )
            }
          }
        }

        // Copyright Card
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = Slate50),
            border = BorderStroke(1.dp, Slate200),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("about_credits_card")
          ) {
            Column(
              modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "© 2026 Gurbani Khoj",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = TextGray,
                  fontSize = 12.sp
                ),
                modifier = Modifier.testTag("copyright_text")
              )
            }
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .width(128.dp)
            .height(6.dp)
            .background(color = Slate200, shape = RoundedCornerShape(3.dp))
        )
      }
    }
  }
}



