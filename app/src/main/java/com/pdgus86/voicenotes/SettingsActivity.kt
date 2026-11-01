package com.pdgus86.voicenotes

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.pdgus86.voicenotes.data.ProviderConnectionTester
import com.pdgus86.voicenotes.data.ProviderRepository
import com.pdgus86.voicenotes.model.Provider
import com.pdgus86.voicenotes.model.ProviderPreset

/**
 * Settings screen, phase 1 skeleton: add, edit, and list AI providers.
 * A provider is a name plus a base URL plus an API key plus a model name,
 * following the Kai and RPClient pattern. Local (on-device) is the
 * offline path: it runs on the phone itself, so it has no URL and no key.
 *
 * Everything here is stored on the phone only. The Test connection
 * button sends one request to that provider, only when tapped.
 * Transcription, summaries, and action items are phase 2.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var repository: ProviderRepository
    private lateinit var adapter: ProviderAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        repository = ProviderRepository(this)
        emptyText = findViewById(R.id.settings_empty_text)

        val recyclerView: RecyclerView = findViewById(R.id.providers_list)
        adapter = ProviderAdapter(
            onEditClicked = { provider -> showProviderDialog(provider) },
            onDeleteClicked = { provider -> showDeleteDialog(provider) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<MaterialButton>(R.id.add_provider_button).setOnClickListener {
            showProviderDialog(null)
        }
        refreshList()
    }

    private fun refreshList() {
        val providers = repository.listProviders()
        adapter.submitProviders(providers)
        emptyText.visibility = if (providers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showDeleteDialog(provider: Provider) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.provider_delete_title))
            .setMessage(getString(R.string.provider_delete_message, provider.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                repository.deleteProvider(provider)
                refreshList()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showProviderDialog(existing: Provider?) {
        val context = this
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val presetNames = ProviderPreset.entries.map { it.displayName }
        val presetSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                presetNames
            )
        }
        fun fieldLabel(textRes: Int): TextView = TextView(context).apply {
            text = getString(textRes)
            textSize = 14f
            setPadding(0, 16, 0, 4)
        }
        val nameLabel = fieldLabel(R.string.provider_name_label)
        val nameInput = EditText(context).apply {
            hint = getString(R.string.provider_name_hint)
            textSize = 18f
        }
        val urlLabel = fieldLabel(R.string.provider_base_url_label)
        val urlInput = EditText(context).apply {
            hint = getString(R.string.provider_base_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            textSize = 18f
        }
        val keyLabel = fieldLabel(R.string.provider_api_key_label)
        val keyInput = EditText(context).apply {
            hint = getString(R.string.provider_api_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 18f
        }
        val modelLabel = fieldLabel(R.string.provider_model_label)
        val modelInput = EditText(context).apply {
            hint = getString(R.string.provider_model_hint)
            textSize = 18f
        }
        val localNote = TextView(context).apply {
            text = getString(R.string.provider_local_note)
            textSize = 16f
            visibility = View.GONE
        }
        val testButton = MaterialButton(context).apply {
            text = getString(R.string.provider_test_connection)
            visibility = View.GONE
        }
        val testResult = TextView(context).apply {
            textSize = 16f
            visibility = View.GONE
        }

        fun applyPreset(preset: ProviderPreset) {
            val isLocal = preset.isLocal
            urlLabel.visibility = if (isLocal) View.GONE else View.VISIBLE
            urlInput.visibility = if (isLocal) View.GONE else View.VISIBLE
            keyLabel.visibility = if (isLocal) View.GONE else View.VISIBLE
            keyInput.visibility = if (isLocal) View.GONE else View.VISIBLE
            localNote.visibility = if (isLocal) View.VISIBLE else View.GONE
            testButton.visibility = if (isLocal) View.GONE else View.VISIBLE
            testResult.visibility = View.GONE
            // The name follows the preset until the user types a custom
            // name. A name that is blank or still matches a preset name
            // is a preset name, so it follows the new preset. A custom
            // name is left alone. This also repairs a provider saved
            // with the wrong preset name: opening it shows the preset
            // its URL belongs to, and the name follows it.
            val currentName = nameInput.text.toString()
            if (currentName.isBlank() || currentName in presetNames) {
                nameInput.setText(preset.displayName)
            }
            if (existing == null) {
                // Only prefill on a new provider, never over an edit.
                urlInput.setText(preset.baseUrl)
                modelInput.setText(preset.defaultModel)
            }
        }

        testButton.setOnClickListener {
            val baseUrl = urlInput.text.toString().trim()
            val apiKey = keyInput.text.toString().trim()
            testButton.isEnabled = false
            testButton.text = getString(R.string.provider_testing)
            testResult.visibility = View.VISIBLE
            testResult.text = getString(R.string.provider_testing)
            Thread {
                val result = ProviderConnectionTester.test(baseUrl, apiKey)
                runOnUiThread {
                    testButton.isEnabled = true
                    testButton.text = getString(R.string.provider_test_connection)
                    testResult.text = when (result) {
                        is ProviderConnectionTester.Result.Success ->
                            getString(R.string.provider_test_success)
                        is ProviderConnectionTester.Result.Failure ->
                            when (result.reason) {
                                ProviderConnectionTester.Reason.NO_URL ->
                                    getString(R.string.provider_test_no_url)
                                ProviderConnectionTester.Reason.BAD_URL ->
                                    getString(R.string.provider_test_bad_url)
                                ProviderConnectionTester.Reason.NO_NETWORK ->
                                    getString(R.string.provider_test_no_network)
                                ProviderConnectionTester.Reason.REFUSED ->
                                    getString(R.string.provider_test_refused)
                                ProviderConnectionTester.Reason.TIMEOUT ->
                                    getString(R.string.provider_test_timeout)
                                ProviderConnectionTester.Reason.BAD_KEY ->
                                    getString(R.string.provider_test_bad_key)
                                ProviderConnectionTester.Reason.FORBIDDEN ->
                                    getString(R.string.provider_test_forbidden)
                                ProviderConnectionTester.Reason.NOT_FOUND ->
                                    getString(R.string.provider_test_not_found)
                                ProviderConnectionTester.Reason.SERVER_ERROR ->
                                    getString(
                                        R.string.provider_test_server_error,
                                        result.httpCode
                                    )
                            }
                    }
                }
            }.start()
        }

        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                applyPreset(ProviderPreset.entries[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        if (existing != null) {
            nameInput.setText(existing.name)
            urlInput.setText(existing.baseUrl)
            keyInput.setText(existing.apiKey)
            modelInput.setText(existing.model)
            val presetIndex = when {
                existing.isLocal -> ProviderPreset.entries.indexOf(ProviderPreset.LOCAL)
                existing.baseUrl == ProviderPreset.OPEN_ROUTER.baseUrl ->
                    ProviderPreset.entries.indexOf(ProviderPreset.OPEN_ROUTER)
                existing.baseUrl == ProviderPreset.OPEN_AI.baseUrl ->
                    ProviderPreset.entries.indexOf(ProviderPreset.OPEN_AI)
                existing.baseUrl == ProviderPreset.DEEP_SEEK.baseUrl ->
                    ProviderPreset.entries.indexOf(ProviderPreset.DEEP_SEEK)
                else -> ProviderPreset.entries.indexOf(ProviderPreset.CUSTOM)
            }
            presetSpinner.setSelection(maxOf(presetIndex, 0))
        } else {
            presetSpinner.setSelection(ProviderPreset.entries.indexOf(ProviderPreset.LOCAL))
        }

        container.addView(presetSpinner)
        container.addView(nameLabel)
        container.addView(nameInput)
        container.addView(urlLabel)
        container.addView(urlInput)
        container.addView(keyLabel)
        container.addView(keyInput)
        container.addView(modelLabel)
        container.addView(modelInput)
        container.addView(localNote)
        container.addView(testButton)
        container.addView(testResult)

        AlertDialog.Builder(this)
            .setTitle(
                if (existing == null) {
                    getString(R.string.add_provider)
                } else {
                    getString(R.string.edit_provider)
                }
            )
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val preset = ProviderPreset.entries[presetSpinner.selectedItemPosition]
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val provider = Provider(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    baseUrl = if (preset.isLocal) "" else urlInput.text.toString().trim(),
                    apiKey = if (preset.isLocal) "" else keyInput.text.toString().trim(),
                    model = modelInput.text.toString().trim(),
                    isLocal = preset.isLocal
                )
                repository.saveProvider(provider)
                refreshList()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private class ProviderAdapter(
        private val onEditClicked: (Provider) -> Unit,
        private val onDeleteClicked: (Provider) -> Unit
    ) : RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {

        private var providers: List<Provider> = emptyList()

        fun submitProviders(providers: List<Provider>) {
            this.providers = providers
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_provider, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val provider = providers[position]
            val context = holder.itemView.context
            holder.nameText.text = provider.name
            holder.modelText.text = provider.model.ifBlank {
                context.getString(R.string.provider_no_model)
            }
            holder.detailText.text = if (provider.isLocal) {
                context.getString(R.string.provider_on_device)
            } else {
                provider.baseUrl.ifBlank {
                    context.getString(R.string.provider_no_url)
                }
            }
            holder.itemView.setOnClickListener { onEditClicked(provider) }
            holder.editButton.setOnClickListener { onEditClicked(provider) }
            holder.deleteButton.setOnClickListener { onDeleteClicked(provider) }
        }

        override fun getItemCount(): Int = providers.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.provider_name)
            val modelText: TextView = view.findViewById(R.id.provider_model)
            val detailText: TextView = view.findViewById(R.id.provider_detail)
            val editButton: ImageButton = view.findViewById(R.id.provider_edit_button)
            val deleteButton: ImageButton = view.findViewById(R.id.provider_delete_button)
        }
    }
}
