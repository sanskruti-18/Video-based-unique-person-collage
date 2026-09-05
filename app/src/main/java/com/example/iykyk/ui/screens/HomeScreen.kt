package com.example.iykyk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun HomeScreen(
    onSelectVideo: () -> Unit
) {

    val background =
        Color(0xFF080C13)

    val cardColor =
        Color(0xFF151C27)

    val accent =
        Color(0xFF55B9F3)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = background
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 24.dp
                )
                .padding(
                    top = 42.dp,
                    bottom = 28.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            // ----------------------------------------------------
            // LOGO
            // ----------------------------------------------------

            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF0879B5),
                                Color(0xFF23B7E8)
                            )
                        )
                    ),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text = "C",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight =
                        FontWeight.ExtraBold
                )
            }

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            Text(
                text = "Collage Maker",
                fontSize = 36.sp,
                fontWeight =
                    FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = Color.White
            )

            Text(
                text = "Capture Your Faces",
                fontSize = 17.sp,
                fontWeight =
                    FontWeight.Medium,
                color = accent,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text =
                    "Turn a portrait video into a\n" +
                            "beautiful people collage.",
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color =
                    Color(0xFF9CA8B8),
                textAlign =
                    TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(30.dp)
            )

            // ----------------------------------------------------
            // FEATURE CARD
            // ----------------------------------------------------

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(26.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            cardColor
                    )
            ) {

                Column(
                    modifier = Modifier.padding(
                        22.dp
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(22.dp)
                ) {

                    Text(
                        text = "How it works",
                        fontSize = 21.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color = Color.White
                    )

                    HomeFeature(
                        number = "01",
                        title = "Detect faces",
                        description =
                            "Finds visible faces across the video using on-device ML."
                    )

                    HomeFeature(
                        number = "02",
                        title = "Recognize people",
                        description =
                            "Face embeddings group the same person across different scenes."
                    )

                    HomeFeature(
                        number = "03",
                        title = "Pick the best shot",
                        description =
                            "Chooses a sharp, frontal and expressive representative frame."
                    )

                    HomeFeature(
                        number = "04",
                        title = "Build your collage",
                        description =
                            "Creates a shareable Instagram Story-style collage automatically."
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            // ----------------------------------------------------
            // SELECT VIDEO BUTTON
            // ----------------------------------------------------

            Button(
                onClick = onSelectVideo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape =
                    RoundedCornerShape(20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            accent,
                        contentColor =
                            Color(0xFF06121C)
                    )
            ) {

                Text(
                    text = "Select Portrait Video",
                    fontSize = 18.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            // ----------------------------------------------------
            // PRIVACY
            // ----------------------------------------------------

            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.Center
            ) {

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            Color(0xFF42D392)
                        )
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    text =
                        "100% on-device • Your video stays private",
                    fontSize = 13.sp,
                    color =
                        Color(0xFF8E99A8),
                    textAlign =
                        TextAlign.Center
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    "No uploads. No cloud processing.",
                fontSize = 12.sp,
                color =
                    Color(0xFF626D7B)
            )
        }
    }
}


@Composable
private fun HomeFeature(
    number: String,
    title: String,
    description: String
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.Top
    ) {

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Color(0xFF0C5279)
                ),
            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = number,
                fontSize = 11.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    Color(0xFF68C9F7)
            )
        }

        Spacer(
            modifier = Modifier.width(14.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight =
                    FontWeight.Bold,
                color = Color.White
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = description,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color =
                    Color(0xFF98A4B4)
            )
        }
    }
}