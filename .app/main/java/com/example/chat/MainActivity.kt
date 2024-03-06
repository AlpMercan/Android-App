package com.example.chat

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.navigation.*
import androidx.navigation.compose.*
import androidx.compose.ui.unit.dp
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.IOException
import java.io.FileOutputStream
import coil.compose.AsyncImage
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp()
        }
    }
}

interface OpenAIService {
    @Headers("Authorization: Bearer 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")
    @POST("v1/chat/completions")
    suspend fun getPrediction(@Body request: ChatGPTRequest): Response<ChatGPTResponse>
}
@Composable
fun MyApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "signIn") {
        composable("signIn") { SignInCreateScreen(navController) }
        composable("main") { MainScreen(navController) }
        composable("threeCards") { ThreeCardsScreen(context = LocalContext.current) }

        composable("detail/{placeholderName}") { backStackEntry ->
            // You'll need to update this logic to call the correct screen based on the placeholderName
            val placeholderName = backStackEntry.arguments?.getString("placeholderName") ?: return@composable
            when (placeholderName) {
                "3 Cards" -> ThreeCardsScreen(context = LocalContext.current)
                // Handle other placeholder names if necessary
                else -> DetailScreen(placeholderName = placeholderName)
            }
        }
    }
}

@Composable
fun MainScreen(navController: NavHostController) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = { navController.navigate("threeCards") }) { // Updated this line
            Text("3 Cards")
        }
        Button(onClick = { navController.navigate("detail/Celtic") }) {
            Text("Celtic")
        }
        Button(onClick = { navController.navigate("detail/Daily") }) {
            Text("Daily")
        }
    }
}

@Composable
fun DetailScreen(placeholderName: String) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = placeholderName, style = MaterialTheme.typography.headlineLarge)

    }
}


@Composable
fun SignInCreateScreen(navController: NavHostController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("User Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)

        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        Button(
            onClick = {
                if (username == "admin" && password == "123456") {
                    navController.navigate("main")
                } else {
                    errorText = "Invalid credentials. Please try again."
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "Sign In")
        }

        if (errorText.isNotEmpty()) {
            Text(text = errorText, color = MaterialTheme.colorScheme.error)
        }
    }
}
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS) // Increase connection timeout
    .readTimeout(60, TimeUnit.SECONDS)    // Increase read timeout
    .writeTimeout(60, TimeUnit.SECONDS)   // Increase write timeout
    .build()

class CardViewModel(application: Application) : AndroidViewModel(application) {
    private val _cards = MutableLiveData<List<Card>>()
    val cards: LiveData<List<Card>> = _cards


    // LiveData to hold analysis result
    private val _analysisResult = MutableLiveData<String>()
    val analysisResult: LiveData<String> = _analysisResult


    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val openAIService: OpenAIService = retrofit.create(OpenAIService::class.java)

    init {
        loadCards()
    }

    private fun loadCards() {
        viewModelScope.launch {
            try {
                val dbHelper = DatabaseHelper(getApplication())
                val randomCards = withContext(Dispatchers.IO) {
                    dbHelper.getRandomCards()
                }
                _cards.value = randomCards
            } catch (e: Exception) {
                Log.e("CardViewModel", "Error loading cards", e)
            }
        }
    }

    fun analyzeCardsWithChatGPT() {
        viewModelScope.launch {
            val cardsList = _cards.value
            if (!cardsList.isNullOrEmpty() && cardsList.size >= 3) {
                try {
                    val systemMessage = "The tarot reading will analyze past, present, and future."
                    //val systemMessage = "chatting app."
                    val userPrompt = "Past: ${cardsList[0].name}, Present: ${cardsList[1].name}, Future: ${cardsList[2].name}. Can you provide insights?"
                    //val userPrompt = "hello"

                    val messages = listOf(
                        ChatGPTRequest.Message(
                            role = "system",
                            content = systemMessage
                        ),
                        ChatGPTRequest.Message(
                            role = "user",
                            content = userPrompt
                        )
                    )


                    // Create the request with the model parameter and the messages
                    val request = ChatGPTRequest(
                        model = "gpt-4",
                        messages = messages // Use the variable here
                    )

                    // Making the API call with the request object
                    val response = openAIService.getPrediction(request)
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        Log.d("CardViewModel", "Response: $responseBody")
                        val resultText = responseBody?.choices?.firstOrNull()?.text ?: "No response"
                        _analysisResult.postValue(resultText)
                    } else {
                        // Log the entire error body for more information
                        val errorBody = response.errorBody()?.string()
                        Log.e("CardViewModel", "API Error Response: $errorBody")
                        _analysisResult.postValue("API Error: $errorBody")
                    }

                } catch (e: Exception) {
                    Log.e("CardViewModel", "API request failed", e)
                    _analysisResult.postValue("Failed to get analysis. Please try again.")
                }
            } else {
                _analysisResult.postValue("Not enough cards selected.")
            }
        }
    }
}

@Composable
fun ThreeCardsScreen(context: Context, viewModel: CardViewModel = viewModel()) {
    // This observes the LiveData and re-composes whenever the LiveData's data changes
    val cards by viewModel.cards.observeAsState(initial = emptyList())
    val analysisResult by viewModel.analysisResult.observeAsState("")

    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            // Display each card in the row
            cards.forEach { card ->
                CardView(card = card)
            }
        }
        Button(onClick = { viewModel.analyzeCardsWithChatGPT() }) {
            Text("Analyze This")
        }
        // Display the analysis result
        if (analysisResult.isNotEmpty()) {
            Text(text = analysisResult, modifier = Modifier.padding(16.dp))
        }
    }
}
data class ChatGPTRequest(
    val model: String,
    val messages: List<Message>
) {
    data class Message(
        val role: String,
        val content: String
    )
}
data class ChatGPTResponse(
    val choices: List<Choice>
) {
    data class Choice(
        val text: String
    )
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyApp()
}
data class Card(
    val name: String,
    val imageUrl: String
)


fun openDatabase(context: Context): SQLiteDatabase {
    val dbFile = context.getDatabasePath("First_database.db")

    if (!dbFile.exists()) {
        try {
            val checkDB = context.openOrCreateDatabase("First_database.db", Context.MODE_PRIVATE, null)
            checkDB?.close()
            copyDatabase(context)
        } catch (e: IOException) {
            throw RuntimeException("Error opening db", e)
        }
    }

    return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
}

private fun copyDatabase(context: Context) {
    val inputStream = context.assets.open("First_database.db")
    val outputFile = context.getDatabasePath("First_database.db")
    val outputStream = FileOutputStream(outputFile)

    val buffer = ByteArray(1024)
    while (inputStream.read(buffer) > 0) {
        outputStream.write(buffer)
    }

    outputStream.flush()
    outputStream.close()
    inputStream.close()
}

@Composable
fun CardView(card: Card) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = card.imageUrl,
            contentDescription = card.name,
            modifier = Modifier.size(100.dp)
        )
        Text(text = card.name)
    }
}
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "First_database.db"
        private const val TABLE_CARDS = "Cards"
        private const val COLUMN_NAME = "Name" // Replace with your actual column name for card name
        private const val COLUMN_IMAGE_URL = "Image_URL" // Replace with your actual column name for image URL
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Not required as the database is pre-created
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle any database upgrade logic here
    }

    init {
        // Check if the database exists before copying it from the assets folder
        val dbFile = context.getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists()) {
            copyDatabase(context)
        }
    }

    private fun copyDatabase(context: Context) {
        val inputStream = context.assets.open(DATABASE_NAME)
        val outputFile = context.getDatabasePath(DATABASE_NAME)
        val outputStream = FileOutputStream(outputFile)

        val buffer = ByteArray(1024)
        while (inputStream.read(buffer) > 0) {
            outputStream.write(buffer)
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()
    }

    fun getRandomCards(): List<Card> {
        val cardsList = mutableListOf<Card>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_CARDS ORDER BY RANDOM() LIMIT 3", null)

        if (cursor.moveToFirst()) {
            do {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))
                val imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_URL))
                cardsList.add(Card(name, imageUrl))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return cardsList
    }
}
