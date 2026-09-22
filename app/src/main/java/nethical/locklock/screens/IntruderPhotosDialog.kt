package nethical.locklock.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nethical.locklock.utils.deleteAllIntruderPhotos
import nethical.locklock.utils.deleteIntruderPhoto
import nethical.locklock.utils.listIntruderPhotos
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Lets the real owner review (and delete) the photos silently captured on
 * wrong-passcode attempts. Only reachable through Settings, which itself
 * only opens once the app has already been unlocked — never shown to
 * whoever entered the wrong passcode in the first place.
 */
@Composable
fun IntruderPhotosDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(listIntruderPhotos(context)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Intruder Photos",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Taken automatically when the wrong passcode was entered. Stored only here — never in your Gallery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (photos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No intruder photos yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(photos) { file ->
                            IntruderPhotoThumbnail(
                                file = file,
                                onDelete = {
                                    deleteIntruderPhoto(file)
                                    photos = listIntruderPhotos(context)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            deleteAllIntruderPhotos(context)
                            photos = listIntruderPhotos(context)
                        }
                    ) {
                        Text("Clear all")
                    }
                }
            }
        }
    }
}

@Composable
private fun IntruderPhotoThumbnail(file: File, onDelete: () -> Unit) {
    val bitmap = remember(file.absolutePath) {
        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
    }
    val dateLabel = remember(file.lastModified()) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(file.lastModified())
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Intruder photo taken $dateLabel",
                modifier = Modifier.fillMaxSize()
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete",
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
