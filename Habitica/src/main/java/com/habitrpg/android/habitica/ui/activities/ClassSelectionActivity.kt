package com.habitrpg.android.habitica.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.rpgClassSelectScreen.CSVMState
import com.habitrpg.android.habitica.rpgClassSelectScreen.ClassSelectionCargo
import com.habitrpg.android.habitica.rpgClassSelectScreen.ClassSelectionViewModel
import com.habitrpg.android.habitica.rpgClassSelectScreen.RpgClassItem
import com.habitrpg.android.habitica.rpgClassSelectScreen.RpgClassProvider
import com.habitrpg.common.habitica.helpersCommon.MainNavigationController
import com.habitrpg.common.habitica.themeCommon.HabiticaTheme
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class ClassSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HabiticaTheme {
                val viewModel by viewModels<ClassSelectionViewModel>()
                val state = viewModel.state

                Screen(state, viewModel::onAnyClk)

                LaunchedEffect(key1 = state.shouldNavigateBack) {
                    if (state.shouldNavigateBack) {
                        finish()
                        MainNavigationController.navigateBack()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Screen(
    state: CSVMState,
    onAnyClk: (ClassSelectionCargo) -> Unit
) {
    Scaffold(
        topBar = {},
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .background(MaterialTheme.colorScheme.onBackground)
                    .padding(innerPadding),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RpgClassProvider.listOfClasses().forEach { rpgClass ->
                    RpgClassItem(
                        rpgClass,
                        Modifier
                            .weight(0.25f)
                            .aspectRatio(1f),
                        state,
                        onAnyClk
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .background(
                        colorResource(
                            id = state.currentClass.rpgColor
                        ).copy(alpha = 0.5f)
                    )
            ) {
                Button(
                    modifier = Modifier
                        .width(150.dp)
                        .height(50.dp)
                        .offset(y = (-25).dp)
                        .border(5.dp, color = Color.White
                            , shape = CircleShape),
                    onClick = { onAnyClk(ClassSelectionCargo.Confirm) }) {
                    Text(
                        text = stringResource(id = R.string.accept),
                        style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    text = stringResource(id = state.currentClass.textDescription),
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight(700),
                    fontSize = 22.sp,
                    lineHeight = 26.sp
                )
            }
        }
    }
}


