package com.example.iykyk.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.iykyk.model.Person
import androidx.compose.material.icons.filled.Save

private val ResultBackground =
    Color(0xFF090D16)

private val ResultCard =
    Color(0xFF151B27)

private val ResultCardLight =
    Color(0xFF1C2432)

private val AccentBlue =
    Color(0xFF64C7FF)

private val AccentBlueDark =
    Color(0xFF07577D)

private val TextPrimary =
    Color(0xFFF5F7FA)

private val TextSecondary =
    Color(0xFF9CA8B8)


@Composable
fun ResultScreen(
    people: List<Person>,
    collage: Bitmap?,
    onSaveToGallery: () -> Unit,
    onShareCollage: () -> Unit,
    onSelectAnother: () -> Unit
) {

    val totalAppearances =
        people.sumOf {
            it.appearanceCount
        }


    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = ResultBackground
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .navigationBarsPadding(),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            // =====================================================
            // HEADER
            // =====================================================

            item {

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                AccentBlueDark
                            ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Check,

                            contentDescription =
                                null,

                            tint =
                                Color.White,

                            modifier =
                                Modifier.size(23.dp)
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.width(12.dp)
                    )


                    Column {

                        Text(
                            text =
                                "Processing Complete",

                            fontSize =
                                25.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                TextPrimary
                        )

                        Text(
                            text =
                                "Your people are ready",

                            fontSize =
                                14.sp,

                            color =
                                TextSecondary
                        )
                    }
                }


                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )
            }


            // =====================================================
            // STATS
            // =====================================================

            item {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    ResultStatCard(
                        modifier =
                            Modifier.weight(1f),

                        value =
                            people.size.toString(),

                        label =
                            "PEOPLE"
                    )


                    ResultStatCard(
                        modifier =
                            Modifier.weight(1f),

                        value =
                            totalAppearances.toString(),

                        label =
                            "APPEARANCES"
                    )
                }
            }


            // =====================================================
            // COLLAGE TITLE
            // =====================================================

            item {

                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )


                    Text(
                        text =
                            "Your Collage",

                        fontSize =
                            26.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            TextPrimary
                    )


                    Text(
                        text =
                            "One memorable shot from every person",

                        fontSize =
                            13.sp,

                        color =
                            TextSecondary
                    )
                }
            }


            // =====================================================
            // COLLAGE
            // =====================================================

            item {

                if (collage != null) {

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        24.dp
                                    )
                                ),

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Color(0xFF0D1320)
                            ),

                        elevation =
                            CardDefaults.cardElevation(
                                defaultElevation =
                                    8.dp
                            )
                    ) {

                        Image(
                            bitmap =
                                collage.asImageBitmap(),

                            contentDescription =
                                "Generated IYKYK collage",

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(
                                        RoundedCornerShape(
                                            24.dp
                                        )
                                    ),

                            contentScale =
                                ContentScale.FillWidth
                        )
                    }
                }
            }


            // =====================================================
            // SAVE / SHARE
            // =====================================================

            item {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    Button(
                        onClick =
                            onSaveToGallery,

                        modifier =
                            Modifier
                                .weight(1f)
                                .height(54.dp),

                        shape =
                            RoundedCornerShape(
                                17.dp
                            ),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    AccentBlue
                            )
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Save,

                            contentDescription =
                                null,

                            tint =
                                Color(0xFF07111C)
                        )


                        Spacer(
                            modifier =
                                Modifier.width(7.dp)
                        )


                        Text(
                            text =
                                "Save",

                            fontSize =
                                16.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(0xFF07111C)
                        )
                    }


                    Button(
                        onClick =
                            onShareCollage,

                        modifier =
                            Modifier
                                .weight(1f)
                                .height(54.dp),

                        shape =
                            RoundedCornerShape(
                                17.dp
                            ),

                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    Color(0xFFE0ECF7)
                            )
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Share,

                            contentDescription =
                                null,

                            tint =
                                Color(0xFF102235)
                        )


                        Spacer(
                            modifier =
                                Modifier.width(7.dp)
                        )


                        Text(
                            text =
                                "Share",

                            fontSize =
                                16.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(0xFF102235)
                        )
                    }
                }
            }


            // =====================================================
            // APPEARANCE BREAKDOWN
            // =====================================================

            item {

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(
                            22.dp
                        ),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                ResultCard
                        )
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                20.dp
                            )
                    ) {

                        Text(
                            text =
                                "Appearance Breakdown",

                            fontSize =
                                20.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                TextPrimary
                        )


                        Spacer(
                            modifier =
                                Modifier.height(5.dp)
                        )


                        Text(
                            text =
                                "How often each person appears",

                            fontSize =
                                13.sp,

                            color =
                                TextSecondary
                        )


                        Spacer(
                            modifier =
                                Modifier.height(15.dp)
                        )


                        HorizontalDivider(
                            color =
                                Color.White.copy(
                                    alpha = 0.10f
                                )
                        )


                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )


                        people.forEachIndexed {
                                index,
                                person ->

                            AppearanceRow(
                                number =
                                    index + 1,

                                appearances =
                                    person.appearanceCount
                            )


                            if (
                                index <
                                people.lastIndex
                            ) {

                                Spacer(
                                    modifier =
                                        Modifier.height(5.dp)
                                )
                            }
                        }
                    }
                }
            }


            // =====================================================
            // SELECT ANOTHER
            // =====================================================

            item {

                OutlinedButton(
                    onClick =
                        onSelectAnother,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp),

                    shape =
                        RoundedCornerShape(
                            17.dp
                        ),

                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor =
                                TextPrimary
                        )
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.Refresh,

                        contentDescription =
                            null
                    )


                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )


                    Text(
                        text =
                            "Process Another Video",

                        fontSize =
                            16.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(14.dp)
                )
            }
        }
    }
}


// =============================================================
// STAT CARD
// =============================================================

@Composable
private fun ResultStatCard(
    modifier: Modifier,
    value: String,
    label: String
) {

    Card(
        modifier =
            modifier,

        shape =
            RoundedCornerShape(
                20.dp
            ),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    AccentBlueDark
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 15.dp
                    ),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text =
                    value,

                fontSize =
                    38.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color.White
            )


            Text(
                text =
                    label,

                fontSize =
                    13.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color.White.copy(
                        alpha = 0.90f
                    )
            )
        }
    }
}


// =============================================================
// APPEARANCE ROW
// =============================================================

@Composable
private fun AppearanceRow(
    number: Int,
    appearances: Int
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 7.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        AccentBlueDark
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    number.toString(),

                fontSize =
                    17.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    Color.White
            )
        }


        Spacer(
            modifier =
                Modifier.width(13.dp)
        )


        Text(
            text =
                "Person $number",

            modifier =
                Modifier.weight(1f),

            fontSize =
                16.sp,

            fontWeight =
                FontWeight.SemiBold,

            color =
                TextPrimary
        )


        Text(
            text =
                if (appearances == 1)
                    "1 appearance"
                else
                    "$appearances appearances",

            fontSize =
                15.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                AccentBlue
        )
    }
}