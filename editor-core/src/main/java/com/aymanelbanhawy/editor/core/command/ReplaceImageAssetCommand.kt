package com.aymanelbanhawy.editor.core.command

import com.aymanelbanhawy.editor.core.model.PageEditModel

data class ReplaceImageAssetCommand(
    private val before: PageEditModel,
    private val after: PageEditModel,
) : EditorCommand by UpdatePageEditCommand(before, after)
