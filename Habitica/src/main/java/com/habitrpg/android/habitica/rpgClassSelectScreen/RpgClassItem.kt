package com.habitrpg.android.habitica.rpgClassSelectScreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun RpgClassItem(
    rpgClass: RpgClass,
    modifier: Modifier,
    state: CSVMState,
    onclick: (ClassSelectionCargo) -> Unit
) {
    val shape = if (state.currentClass==rpgClass) 42.dp
    else 16.dp
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(shape),
       border = BorderStroke(
            width = 7.dp,
           color = colorResource(id = rpgClass.rpgColor)
       ),
        modifier = modifier
            .padding(16.dp)
            .clickable { onclick(ClassSelectionCargo.Item(rpgClass)) }
    ) {
        Image(
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(
                id = rpgClass.pic
            ),
            contentDescription = ""
        )
    }
}