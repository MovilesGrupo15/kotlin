package edu.uniandes.ecosnap.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import edu.uniandes.ecosnap.domain.model.Offer

@Composable
fun OfferCard(
    offer: Offer,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (offer.backgroundColor != null)
                Color(offer.backgroundColor) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${offer.points} CoonPoints",
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (offer.imageUrl.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(offer.imageUrl),
                    contentDescription = offer.description,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Text(
                text = offer.description,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}