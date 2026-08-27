package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ResearchScreen()
            }
        }
    }
}

@Composable
fun ResearchScreen() {

    var sampleId by remember {
        mutableStateOf("")
    }

    var sampleName by remember {
        mutableStateOf("")
    }

    // Dynamic state: Create a variable that starts at 0.0. Remember it so it doesn't reset when the screen redraws.
    // Make it a State so that every time when the value changes, the UI is triggered to auto update to show the new number.
    // The by here allows me to use it like a normal variable.
    var measurementValue by remember {
        mutableStateOf<Double?>(null)
    }

    var measurementCount by remember {
        mutableIntStateOf(0)
    }

    val deviceStatus = "Connected"

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Research Measurement App",
            fontSize = 26.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = sampleId, // The display: show the current sampleId value.
            onValueChange = {
                sampleId = it // ‘it’ is a special Kotlin keyword that represents the new text that just arrived from the keyboard.
            },
            label = {
                Text("Sample ID") // The label of the text field to guide the input
            }
        )

        OutlinedTextField(
            value = sampleName,
            onValueChange = {
                sampleName = it
            },
            label = {
                Text("SampleName")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Device status: $deviceStatus")

        Spacer(modifier = Modifier.height(16.dp))

        if (sampleId.isBlank()) {
            Text("Please enter a sample ID before measuring.")
        }

        Card( // A container that presents the parts inside with a shadow to highlight
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Current value")

                Text(
                    text = measurementValue?.let { // let: "enter" the curly braces if the safe actually had a value inside.
                        "%.3f".format(it) // formatted number 'it' refers the measurementValue
                    } ?: "--", // If measurementValue is not null: format it; Otherwise: show "--"
                    fontSize = 40.sp
                )

                Text("Measurements: $measurementCount")
            }
        }

        Text(
            text = measurementValue?.toString() ?: "No measurement yet"
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Measurement Count: $measurementCount")

        Spacer(modifier = Modifier.height(16.dp))

        Row (
            modifier = Modifier.fillMaxWidth(),
        ){
            Button(
                onClick = {
                    measurementValue = Random.nextDouble(
                        from = 0.0,
                        until = 5.0
                    )
                    measurementCount = measurementCount + 1
                },
                enabled = sampleId.isNotBlank(), // If sampleId is blank, disable the button.
                modifier = Modifier.weight(1f) // Each button takes an equal share of the row width.
            ) {
                Text("Start Measurement")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = {
                    measurementValue = null
                    measurementCount = 0
                },
                modifier = Modifier.weight(1f) // Each button takes an equal share of the row width.
            ) {
                Text("Reset")
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun ResearchScreenPreview() {
    MaterialTheme {
        ResearchScreen()
    }
}