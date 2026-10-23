package com.softandtech.tuitionmanager.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.domain.security.PinHasher

/** Filled dots for the digits typed so far — the PIN itself is never shown. */
@Composable
fun PinDots(length: Int) {
    val description = stringResource(R.string.pin_digits_entered, length)
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(maxOf(length, PinHasher.PIN_LENGTHS.first)) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .background(
                        if (i < length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

/** Number keypad with delete and OK. [enabled] = false during a lockout. */
@Composable
fun PinPad(
    pin: String,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled) { if (pin.length < PinHasher.PIN_LENGTHS.last) onPinChange(pin + digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onPinChange(pin.dropLast(1)) }, enabled = enabled && pin.isNotEmpty(), modifier = Modifier.size(KEY_SIZE)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.pin_delete))
            }
            DigitKey("0", enabled) { if (pin.length < PinHasher.PIN_LENGTHS.last) onPinChange(pin + "0") }
            FilledTonalIconButton(onClick = onSubmit, enabled = enabled && PinHasher.isValidPin(pin), modifier = Modifier.size(KEY_SIZE)) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.pin_ok))
            }
        }
    }
}

@Composable
private fun DigitKey(digit: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(KEY_SIZE), shape = CircleShape) {
        Text(digit, fontSize = 28.sp)
    }
}

private val KEY_SIZE = 72.dp
