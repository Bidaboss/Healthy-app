package com.bidaboss.healthy.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bidaboss.healthy.AppContainer
import com.bidaboss.healthy.HealthyApplication

/** Crée un ViewModel à partir du conteneur de l'application (injection manuelle). */
inline fun <reified VM : ViewModel> healthyViewModelFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val application = checkNotNull(this[APPLICATION_KEY]) as HealthyApplication
        create(application.container)
    }
}

/** Comme [healthyViewModelFactory], avec un état qui survit à la fermeture de l'app par Android. */
inline fun <reified VM : ViewModel> healthySavedStateViewModelFactory(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val application = checkNotNull(this[APPLICATION_KEY]) as HealthyApplication
        create(application.container, createSavedStateHandle())
    }
}
