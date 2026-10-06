package com.example.myapplication

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 1. Llamamos a la pantalla principal que contiene la lógica de la UI
                    NewsScreen(
                        repository = ConnectionOptimizedNewsRepository(applicationContext),
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun NewsScreen(repository: ConnectionOptimizedNewsRepository, modifier: Modifier = Modifier) {

    var newsList by remember { mutableStateOf<List<NewsArticle>?>(null) }
    val coroutineScope = rememberCoroutineScope()


    LaunchedEffect(key1 = true) {
        coroutineScope.launch {

            val fetchedNews = repository.fetchNews()
            newsList = fetchedNews
        }
    }


    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (newsList == null) {

            CircularProgressIndicator()
            Text("Cargando noticias...", modifier = Modifier.padding(top = 60.dp))
        } else if (newsList!!.isEmpty()) {
            Text("No se encontraron noticias.")
        } else {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(newsList!!) { article ->
                    NewsCard(article = article)
                }
            }
        }
    }
}

@Composable
fun NewsCard(article: NewsArticle) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = article.title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = article.summary, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Calidad de imagen: ${article.imageQuality}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NewsScreenPreview() {
    MyApplicationTheme {

        val fakeArticle = NewsArticle("1", "Título de Noticia", "Este es un resumen.", "url", "high")
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(5) {
                NewsCard(article = fakeArticle)
            }
        }
    }
}
data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val imageUrl: String,
    val imageQuality: String
)

enum class ConnectionType {
    WIFI, CELLULAR_FAST, CELLULAR_SLOW, NO_NETWORK
}

class ConnectionOptimizedNewsRepository(private val context: Context) {

    private val tag = "ConnectionRepository"
    private val apiEndpoint = "https://gnews.io/dashboard"

    private fun getCurrentConnectionType(): ConnectionType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return ConnectionType.NO_NETWORK
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return ConnectionType.NO_NETWORK

        return when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val downloadSpeedMbps = (networkCapabilities.linkDownstreamBandwidthKbps.toDouble() / 1024.0)
                if (downloadSpeedMbps > 2.0) ConnectionType.CELLULAR_FAST else ConnectionType.CELLULAR_SLOW
            }
            else -> ConnectionType.NO_NETWORK
        }
    }

    suspend fun fetchNews(): List<NewsArticle> {
        val connectionType = getCurrentConnectionType()
        Log.d(tag, "Conexión detectada: $connectionType")


        val (pageSize, imageQuality) = when (connectionType) {
            ConnectionType.WIFI -> 20 to "high"
            ConnectionType.CELLULAR_FAST -> 10 to "medium"
            ConnectionType.CELLULAR_SLOW -> 5 to "low"
            ConnectionType.NO_NETWORK -> {
                Log.w(tag, "Modo Offline. Cargando desde caché local.")
                return loadNewsFromCache()
            }
        }


        Log.i(tag, "Estrategia: $connectionType. Cargando $pageSize artículos con calidad '$imageQuality'.")

        val apiUrlWithParams = "$apiEndpoint?size=$pageSize&quality=$imageQuality"
        Log.d(tag, "Llamando API con URL optimizada: $apiUrlWithParams")

        delay(1000)

        return (1..pageSize).map { i ->
            NewsArticle("id_$i", "Noticia $i ($connectionType)", "Resumen para la noticia $i.", "url/$i", imageQuality)
        }
    }

    private fun loadNewsFromCache(): List<NewsArticle> {
        return listOf(NewsArticle("offline_1", "Noticia Offline", "Esta noticia se cargó desde la caché.", "url/placeholder", "cached"))
    }
}
