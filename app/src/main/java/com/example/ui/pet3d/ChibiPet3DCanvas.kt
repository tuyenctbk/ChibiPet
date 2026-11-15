package com.example.ui.pet3d

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.PetOutfit
import com.example.data.model.SpeciesColor
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
@Composable
fun ChibiPet3DCanvas(
    speciesColor: SpeciesColor,
    equippedOutfit: PetOutfit,
    happiness: Int,
    energy: Int,
    celebrationTick: Int,
    onPetTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val renderer = remember { ChibiPet3DRenderer() }

    LaunchedEffect(speciesColor, equippedOutfit, happiness, energy, isDark) {
        renderer.speciesColor = speciesColor
        renderer.equippedOutfit = equippedOutfit
        renderer.happiness = happiness
        renderer.energy = energy
        renderer.isDarkTheme = isDark
    }

    LaunchedEffect(celebrationTick) {
        if (celebrationTick > 0) {
            renderer.triggerCelebration()
        }
    }

    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

            var prevX = 0f
            var prevY = 0f
            var totalMove = 0f
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        prevX = event.x
                        prevY = event.y
                        totalMove = 0f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - prevX
                        val dy = event.y - prevY
                        prevX = event.x
                        prevY = event.y
                        totalMove += abs(dx) + abs(dy)
                        renderer.addOrbitDelta(dx, dy)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (totalMove < 24f) {
                            renderer.triggerBoop()
                            onPetTapped()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView.onPause()
        }
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = modifier
    )
}
