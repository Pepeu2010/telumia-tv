package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.remote.PhoneRemoteStatus
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun PhoneRemoteSettingsContent(
    viewModel: PhoneRemoteSettingsViewModel,
    autoFocusEnabled: Boolean
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(autoFocusEnabled) {
        if (autoFocusEnabled) runCatching { actionFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsDetailHeader(
            title = stringResource(R.string.phone_remote_title),
            subtitle = stringResource(R.string.phone_remote_subtitle)
        )

        SettingsGroupCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(state.status.labelResource()),
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextPrimary
                    )

                    if (state.endpoint != null) {
                        Text(
                            text = stringResource(R.string.phone_remote_address, state.endpoint.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }

                    state.pairingCode?.let { code ->
                        Text(
                            text = stringResource(R.string.phone_remote_pairing_code, code),
                            style = MaterialTheme.typography.headlineMedium,
                            color = NuvioTheme.colors.TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.phone_remote_scan_approval),
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.colors.TextSecondary
                        )
                    }

                    state.pendingPairing?.let { pending ->
                        Text(
                            text = stringResource(
                                R.string.phone_remote_pairing_request,
                                pending.deviceName,
                                pending.peerAddress
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { viewModel.approvePairing(pending.requestId) }) {
                                Text(stringResource(R.string.phone_remote_approve))
                            }
                            Button(onClick = { viewModel.rejectPairing(pending.requestId) }) {
                                Text(stringResource(R.string.phone_remote_reject))
                            }
                        }
                    }

                    when {
                        state.isStarting -> Unit
                        !state.isEnabled -> Button(
                            onClick = viewModel::enable,
                            modifier = Modifier.focusRequester(actionFocusRequester)
                        ) {
                            Text(stringResource(R.string.phone_remote_enable))
                        }
                        state.isEnabled && state.pairingCode == null -> {
                            Button(
                                onClick = viewModel::createNewPairingCode,
                                modifier = Modifier.focusRequester(actionFocusRequester)
                            ) {
                                Text(stringResource(R.string.phone_remote_new_code))
                            }
                            Button(onClick = viewModel::disable) {
                                Text(stringResource(R.string.phone_remote_disable))
                            }
                            SettingsNote(text = stringResource(R.string.phone_remote_new_code_warning))
                        }
                        else -> Button(
                            onClick = viewModel::disable,
                            modifier = Modifier.focusRequester(actionFocusRequester)
                        ) {
                            Text(stringResource(R.string.phone_remote_disable))
                        }
                    }

                    if (state.status == PhoneRemoteStatus.Active ||
                        state.status == PhoneRemoteStatus.PairingPending ||
                        state.status == PhoneRemoteStatus.Paired
                    ) {
                        SettingsNote(text = stringResource(R.string.phone_remote_local_network_only))
                    }
                }

                state.qrCode?.let { qrCode ->
                    Image(
                        bitmap = qrCode.asImageBitmap(),
                        contentDescription = stringResource(R.string.phone_remote_qr_description),
                        modifier = Modifier.size(190.dp)
                    )
                }
            }
        }
    }
}

private fun PhoneRemoteStatus.labelResource(): Int = when (this) {
    PhoneRemoteStatus.Off -> R.string.phone_remote_status_off
    PhoneRemoteStatus.Starting -> R.string.phone_remote_status_starting
    PhoneRemoteStatus.Active -> R.string.phone_remote_status_active
    PhoneRemoteStatus.PairingExpired -> R.string.phone_remote_status_expired
    PhoneRemoteStatus.PairingPending -> R.string.phone_remote_status_pending
    PhoneRemoteStatus.Paired -> R.string.phone_remote_status_paired
    PhoneRemoteStatus.NetworkUnavailable -> R.string.phone_remote_network_unavailable
    PhoneRemoteStatus.ServerError -> R.string.phone_remote_server_error
}
