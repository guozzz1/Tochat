package com.gzzz.toimage.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gzzz.toimage.data.local.PromptTemplateDao
import com.gzzz.toimage.data.local.PromptTemplateEntity
import com.gzzz.toimage.data.template.BuiltInTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val templateDao: PromptTemplateDao
) : ViewModel() {

    val templates: StateFlow<List<PromptTemplateEntity>> =
        templateDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> =
        templateDao.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        initBuiltInTemplates()
    }

    private fun initBuiltInTemplates() {
        viewModelScope.launch {
            if (templateDao.count() == 0) {
                templateDao.insertAll(BuiltInTemplates.getAll())
            }
        }
    }

    fun addCustomTemplate(name: String, category: String, prompt: String, negativePrompt: String?) {
        viewModelScope.launch {
            templateDao.insert(
                PromptTemplateEntity(
                    name = name,
                    category = category,
                    prompt = prompt,
                    negativePrompt = negativePrompt,
                    isBuiltIn = false
                )
            )
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            templateDao.deleteById(id)
        }
    }
}
