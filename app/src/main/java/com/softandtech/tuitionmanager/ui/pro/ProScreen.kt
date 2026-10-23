package com.softandtech.tuitionmanager.ui.pro

import android.app.Activity
import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.softandtech.tuitionmanager.R
import com.softandtech.tuitionmanager.data.billing.BillingStatus
import com.softandtech.tuitionmanager.data.billing.ProRepository
import com.softandtech.tuitionmanager.domain.Plan
import com.softandtech.tuitionmanager.ui.common.BackTopBar
import com.softandtech.tuitionmanager.ui.settings.SwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProViewModel @Inject constructor(
    private val proRepository: ProRepository,
) : ViewModel() {
    val isPro: StateFlow<Boolean> = proRepository.isPro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val price: StateFlow<String?> = proRepository.product
        .map { it?.oneTimePurchaseOfferDetails?.formattedPrice }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val status: StateFlow<BillingStatus> = proRepository.status

    fun refresh() = proRepository.refresh()
    fun buy(activity: Activity) = proRepository.launchPurchase(activity)
    fun setProForTesting(enabled: Boolean) = viewModelScope.launch { proRepository.setPro(enabled) }
}

@Composable
fun ProScreen(onBack: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.pro_title), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isPro) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(stringResource(R.string.pro_owned), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text(stringResource(R.string.pro_pitch), style = MaterialTheme.typography.bodyLarge)
            }

            Benefit(R.string.pro_benefit_students)
            Benefit(R.string.pro_benefit_batches)
            Benefit(R.string.pro_benefit_once)
            Benefit(R.string.pro_benefit_support)

            Text(
                stringResource(R.string.pro_free_includes, Plan.FREE_STUDENT_LIMIT, Plan.FREE_BATCH_LIMIT),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isPro) {
                Button(
                    onClick = { (context as? Activity)?.let(viewModel::buy) },
                    enabled = price != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(price?.let { stringResource(R.string.pro_buy, it) } ?: stringResource(R.string.pro_buy_loading))
                }
                if (status == BillingStatus.UNAVAILABLE) {
                    Text(stringResource(R.string.pro_play_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pro_restore))
                }
            }

            // Debug builds only: lets testers check the free limits without a real Play purchase
            if (debuggable) {
                SwitchRow(
                    title = "Debug: Pro unlocked",
                    help = "Only in debug builds. Simulates owning Pro.",
                    checked = isPro,
                    onChange = viewModel::setProForTesting,
                )
            }
        }
    }
}

@Composable
private fun Benefit(@StringRes text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(text), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Shown when a free-plan limit is reached. */
@Composable
fun UpgradeDialog(@StringRes message: Int, limit: Int, onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upgrade_title)) },
        text = { Text(stringResource(message, limit)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onUpgrade()
            }) { Text(stringResource(R.string.upgrade_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } },
    )
}
