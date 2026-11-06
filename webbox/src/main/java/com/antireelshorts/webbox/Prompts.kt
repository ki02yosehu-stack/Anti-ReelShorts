package com.antireelshorts.webbox

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.EditText
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AlertPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ButtonPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoSession.PromptDelegate.TextPrompt

/**
 * GeckoView has no built-in UI for dialogs, <select> menus or file pickers;
 * without this they would silently do nothing.
 */
class Prompts(private val activity: Activity) : GeckoSession.PromptDelegate {

    private var pendingFile: Pair<FilePrompt, GeckoResult<PromptResponse>>? = null

    override fun onAlertPrompt(session: GeckoSession, prompt: AlertPrompt): GeckoResult<PromptResponse> {
        val result = Once()
        AlertDialog.Builder(activity)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { result.complete(prompt.dismiss()) }
            .show()
        return result.result
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: ButtonPrompt): GeckoResult<PromptResponse> {
        val result = Once()
        AlertDialog.Builder(activity)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                result.complete(prompt.confirm(ButtonPrompt.Type.POSITIVE))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                result.complete(prompt.confirm(ButtonPrompt.Type.NEGATIVE))
            }
            .setOnDismissListener { result.complete(prompt.dismiss()) }
            .show()
        return result.result
    }

    override fun onTextPrompt(session: GeckoSession, prompt: TextPrompt): GeckoResult<PromptResponse> {
        val result = Once()
        val input = EditText(activity).apply { setText(prompt.defaultValue) }
        AlertDialog.Builder(activity)
            .setTitle(prompt.title)
            .setMessage(prompt.message)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                result.complete(prompt.confirm(input.text.toString()))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { result.complete(prompt.dismiss()) }
            .show()
        return result.result
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: ChoicePrompt): GeckoResult<PromptResponse> {
        val result = Once()
        // <optgroup>s are flattened into one list.
        val choices = flatten(prompt.choices).filter { !it.separator }
        val labels = choices.map { it.label }.toTypedArray()
        val builder = AlertDialog.Builder(activity).setTitle(prompt.title)

        if (prompt.type == ChoicePrompt.Type.MULTIPLE) {
            val checked = choices.map { it.selected }.toBooleanArray()
            builder
                .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val ids = choices.filterIndexed { i, _ -> checked[i] }.map { it.id }.toTypedArray()
                    result.complete(prompt.confirm(ids))
                }
        } else {
            val selected = choices.indexOfFirst { it.selected }
            builder.setSingleChoiceItems(labels, selected) { dialog, which ->
                result.complete(prompt.confirm(choices[which].id))
                dialog.dismiss()
            }
        }
        builder.setOnDismissListener { result.complete(prompt.dismiss()) }.show()
        return result.result
    }

    override fun onFilePrompt(session: GeckoSession, prompt: FilePrompt): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        pendingFile?.let { (oldPrompt, oldResult) -> oldResult.complete(oldPrompt.dismiss()) }
        pendingFile = prompt to result

        val mimeTypes = prompt.mimeTypes?.filter { it.isNotBlank() }.orEmpty()
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeTypes.singleOrNull() ?: "*/*"
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, prompt.type == FilePrompt.Type.MULTIPLE)
        }
        activity.startActivityForResult(Intent.createChooser(intent, null), REQUEST_FILE)
        return result
    }

    /** Called from the activity's onActivityResult. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_FILE) return
        val (prompt, result) = pendingFile ?: return
        pendingFile = null

        val uris = mutableListOf<Uri>()
        if (resultCode == Activity.RESULT_OK && data != null) {
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
            } else {
                data.data?.let { uris += it }
            }
        }
        result.complete(
            when {
                uris.isEmpty() -> prompt.dismiss()
                prompt.type == FilePrompt.Type.MULTIPLE -> prompt.confirm(activity, uris.toTypedArray())
                else -> prompt.confirm(activity, uris.first())
            }
        )
    }

    private fun flatten(choices: Array<ChoicePrompt.Choice>): List<ChoicePrompt.Choice> =
        choices.flatMap { choice -> choice.items?.let { flatten(it) } ?: listOf(choice) }

    /** A dialog can both be answered and dismissed; only the first answer counts. */
    private class Once {
        val result = GeckoResult<PromptResponse>()
        private var done = false

        fun complete(response: PromptResponse) {
            if (done) return
            done = true
            result.complete(response)
        }
    }

    private companion object {
        const val REQUEST_FILE = 1
    }
}
