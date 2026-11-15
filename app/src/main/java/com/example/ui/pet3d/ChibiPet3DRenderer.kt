package com.example.ui.pet3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.data.model.PetOutfit
import com.example.data.model.SpeciesColor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class ChibiPet3DRenderer : GLSurfaceView.Renderer {

    @Volatile var speciesColor: SpeciesColor = SpeciesColor.MINT
    @Volatile var equippedOutfit: PetOutfit = PetOutfit.BOW
    @Volatile var happiness: Int = 85
    @Volatile var energy: Int = 90
    @Volatile var isDarkTheme: Boolean = false

    @Volatile private var userYawOffset: Float = 0f
    @Volatile private var userPitchOffset: Float = 8f
    @Volatile private var yawVelocity: Float = 0f
    @Volatile private var boopEnergy: Float = 0f
    @Volatile private var celebrationTimer: Float = 0f
    @Volatile private var cameraZoomPulse: Float = 0f

    private var lastFrameNanos: Long = 0L
    private var elapsedSeconds: Float = 0f

    private var programId = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpMatrixHandle = 0
    private var modelMatrixHandle = 0
    private var colorHandle = 0
    private var lightDirHandle = 0
    private var ambientHandle = 0
    private var specularHandle = 0

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    private lateinit var sphereMesh: Mesh
    private lateinit var coneMesh: Mesh
    private lateinit var cylinderMesh: Mesh
    private lateinit var torusMesh: Mesh
    private lateinit var discMesh: Mesh
    private lateinit var cubeMesh: Mesh

    private data class Particle(
        var x: Float,
        var y: Float,
        var z: Float,
        var vx: Float,
        var vy: Float,
        var vz: Float,
        var rot: Float,
        var vRot: Float,
        var scale: Float,
        var r: Float,
        var g: Float,
        var b: Float,
        var life: Float,
        var maxLife: Float
    )

    private val particles = mutableListOf<Particle>()
    private val particleLock = Any()

    fun addOrbitDelta(dx: Float, dy: Float) {
        userYawOffset += dx * 0.45f
        yawVelocity = dx * 0.35f
        userPitchOffset = (userPitchOffset + dy * 0.2f).coerceIn(-18f, 34f)
    }

    fun triggerBoop() {
        boopEnergy = 1.0f
        cameraZoomPulse = 0.22f
        synchronized(particleLock) {
            repeat(10) {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                particles.add(
                    Particle(
                        x = cos(angle) * 0.25f,
                        y = 0.72f + Random.nextFloat() * 0.3f,
                        z = sin(angle) * 0.25f,
                        vx = cos(angle) * (0.4f + Random.nextFloat() * 0.5f),
                        vy = 1.15f + Random.nextFloat() * 0.8f,
                        vz = sin(angle) * (0.4f + Random.nextFloat() * 0.5f),
                        rot = Random.nextFloat() * 360f,
                        vRot = (Random.nextFloat() - 0.5f) * 240f,
                        scale = 0.06f,
                        r = 1.0f,
                        g = 0.48f,
                        b = 0.68f,
                        life = 1.2f,
                        maxLife = 1.2f
                    )
                )
            }
        }
    }

    fun triggerCelebration() {
        celebrationTimer = 3.0f
        boopEnergy = 1.25f
        cameraZoomPulse = 0.35f
        val palette = listOf(
            floatArrayOf(1.0f, 0.45f, 0.55f),
            floatArrayOf(0.35f, 0.88f, 0.70f),
            floatArrayOf(1.0f, 0.82f, 0.28f),
            floatArrayOf(0.55f, 0.65f, 1.0f),
            floatArrayOf(1.0f, 0.62f, 0.35f)
        )
        synchronized(particleLock) {
            repeat(38) {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val speed = 0.75f + Random.nextFloat() * 1.45f
                val c = palette[it % palette.size]
                particles.add(
                    Particle(
                        x = 0f,
                        y = 0.55f,
                        z = 0f,
                        vx = cos(angle) * speed,
                        vy = 2.2f + Random.nextFloat() * 1.9f,
                        vz = sin(angle) * speed,
                        rot = Random.nextFloat() * 360f,
                        vRot = (Random.nextFloat() - 0.5f) * 440f,
                        scale = 0.055f + Random.nextFloat() * 0.03f,
                        r = c[0],
                        g = c[1],
                        b = c[2],
                        life = 2.5f,
                        maxLife = 2.5f
                    )
                )
            }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        programId = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(programId, "aNormal")
        mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
        modelMatrixHandle = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        colorHandle = GLES20.glGetUniformLocation(programId, "uColor")
        lightDirHandle = GLES20.glGetUniformLocation(programId, "uLightDir")
        ambientHandle = GLES20.glGetUniformLocation(programId, "uAmbient")
        specularHandle = GLES20.glGetUniformLocation(programId, "uSpecular")

        sphereMesh = MeshBuilder.createSphere(24, 24)
        coneMesh = MeshBuilder.createCone(20)
        cylinderMesh = MeshBuilder.createCylinder(20)
        torusMesh = MeshBuilder.createTorus(24, 14, 0.32f)
        discMesh = MeshBuilder.createDisc(28)
        cubeMesh = MeshBuilder.createCube()

        lastFrameNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 38f, ratio, 0.5f, 20f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.04f)
        lastFrameNanos = now
        elapsedSeconds += dt

        userYawOffset += yawVelocity
        yawVelocity *= 0.90f
        if (abs(yawVelocity) < 0.02f) {
            userYawOffset += sin(elapsedSeconds * 0.65f) * 0.03f
        }
        if (boopEnergy > 0f) {
            boopEnergy = (boopEnergy - dt * 2.2f).coerceAtLeast(0f)
        }
        if (celebrationTimer > 0f) {
            celebrationTimer = (celebrationTimer - dt).coerceAtLeast(0f)
        }
        if (cameraZoomPulse > 0f) {
            cameraZoomPulse = (cameraZoomPulse - dt * 0.6f).coerceAtLeast(0f)
        }

        if (isDarkTheme) {
            GLES20.glClearColor(0.14f, 0.13f, 0.21f, 1f)
        } else {
            GLES20.glClearColor(1.0f, 0.96f, 0.92f, 1f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(programId)

        val camZ = 3.65f - cameraZoomPulse
        val camY = 0.72f
        Matrix.setLookAtM(viewMatrix, 0, 0f, camY, camZ, 0f, 0.38f, 0f, 0f, 1f, 0f)

        GLES20.glUniform3f(lightDirHandle, 0.45f, 0.85f, 0.65f)
        GLES20.glUniform1f(ambientHandle, 0.54f)

        val isCelebrating = celebrationTimer > 0f
        val celebProgress = if (isCelebrating) (3.0f - celebrationTimer) else 0f
        val idleHopAmp = if (happiness >= 80) 0.048f else 0.025f
        val jumpHeight = if (isCelebrating) {
            abs(sin(celebProgress * 7.2f)) * 0.44f
        } else {
            sin(elapsedSeconds * 2.8f) * idleHopAmp
        }
        val spinAngle = if (isCelebrating) {
            celebProgress * 360f
        } else {
            0f
        }
        val squash = if (boopEnergy > 0f) {
            sin(boopEnergy * PI.toFloat() * 3f) * boopEnergy * 0.15f
        } else {
            0f
        }
        val scaleY = 1f - squash
        val scaleXZ = 1f + squash * 0.55f

        drawPedestalAndShadow(jumpHeight)

        drawPetCharacter(
            baseX = 0f,
            baseY = -0.15f + jumpHeight,
            baseZ = 0f,
            yaw = userYawOffset + spinAngle,
            pitch = userPitchOffset,
            scaleXZ = scaleXZ,
            scaleY = scaleY,
            isCelebrating = isCelebrating
        )

        // Orbiting happy star when mood >= 85
        if (happiness >= 85 || isCelebrating) {
            val orbitAngle = elapsedSeconds * 1.9f
            val starX = cos(orbitAngle) * 0.82f
            val starZ = sin(orbitAngle) * 0.82f
            val starY = 0.65f + sin(elapsedSeconds * 3.5f) * 0.09f
            drawPart(
                mesh = cubeMesh,
                tx = starX, ty = starY, tz = starZ,
                rx = elapsedSeconds * 90f, ry = elapsedSeconds * 120f, rz = 0f,
                sx = 0.07f, sy = 0.07f, sz = 0.07f,
                rgba = floatArrayOf(1.0f, 0.84f, 0.30f, 1f),
                specular = 0.9f
            )
        }

        // Transparent particles with depth write disabled to avoid Z-clipping
        GLES20.glDepthMask(false)
        updateAndDrawParticles(dt)
        GLES20.glDepthMask(true)
    }

    private fun drawPedestalAndShadow(jumpHeight: Float) {
        val padColor = if (isDarkTheme) floatArrayOf(0.20f, 0.19f, 0.30f, 1f) else floatArrayOf(0.97f, 0.90f, 0.84f, 1f)
        drawPart(
            mesh = discMesh,
            tx = 0f, ty = -0.58f, tz = 0f,
            rx = 0f, ry = 0f, rz = 0f,
            sx = 1.15f, sy = 0.08f, sz = 1.15f,
            rgba = padColor,
            specular = 0.05f
        )

        // Glowing golden pedestal trim ring
        val trimColor = if (isDarkTheme) floatArrayOf(0.42f, 0.88f, 0.75f, 0.55f) else floatArrayOf(0.96f, 0.72f, 0.36f, 0.65f)
        drawPart(
            mesh = torusMesh,
            tx = 0f, ty = -0.56f, tz = 0f,
            rx = 90f, ry = 0f, rz = elapsedSeconds * 14f,
            sx = 0.94f, sy = 0.94f, sz = 0.10f,
            rgba = trimColor,
            specular = 0.7f
        )

        val shadowScale = (0.58f - jumpHeight * 0.35f).coerceIn(0.32f, 0.62f)
        val shadowColor = if (isDarkTheme) floatArrayOf(0.08f, 0.07f, 0.12f, 0.50f) else floatArrayOf(0.84f, 0.74f, 0.67f, 0.55f)
        GLES20.glDepthMask(false)
        drawPart(
            mesh = discMesh,
            tx = 0f, ty = -0.535f, tz = 0f,
            rx = 0f, ry = 0f, rz = 0f,
            sx = shadowScale, sy = 0.02f, sz = shadowScale * 0.9f,
            rgba = shadowColor,
            specular = 0.0f
        )
        GLES20.glDepthMask(true)
    }

    private fun drawPetCharacter(
        baseX: Float,
        baseY: Float,
        baseZ: Float,
        yaw: Float,
        pitch: Float,
        scaleXZ: Float,
        scaleY: Float,
        isCelebrating: Boolean
    ) {
        val primary = hexToRgba(speciesColor.primaryHex)
        val belly = hexToRgba(speciesColor.bellyHex)
        val cheek = hexToRgba(speciesColor.cheekHex)
        val darkInk = floatArrayOf(0.14f, 0.12f, 0.20f, 1f)
        val white = floatArrayOf(1f, 1f, 1f, 1f)

        val root = FloatArray(16)
        Matrix.setIdentityM(root, 0)
        Matrix.translateM(root, 0, baseX, baseY, baseZ)
        Matrix.rotateM(root, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(root, 0, yaw, 0f, 1f, 0f)
        Matrix.scaleM(root, 0, scaleXZ, scaleY, scaleXZ)

        // Torso & breathing belly pulse
        val breathPulse = 1f + sin(elapsedSeconds * 3.1f) * 0.026f
        drawChild(root, sphereMesh, 0f, 0f, 0f, 0f, 0f, 0f, 0.44f * breathPulse, 0.42f, 0.40f * breathPulse, primary, 0.35f)
        drawChild(root, sphereMesh, 0f, -0.04f, 0.16f, 0f, 0f, 0f, 0.31f * breathPulse, 0.30f, 0.28f * breathPulse, belly, 0.15f)
        // Fluffy chest tuft
        drawChild(root, sphereMesh, 0f, 0.16f, 0.31f, 0f, 0f, 0f, 0.14f, 0.11f, 0.10f, belly, 0.20f)

        // Feet
        val footBounce = if (isCelebrating) sin(elapsedSeconds * 16f) * 18f else 0f
        drawChild(root, sphereMesh, -0.20f, -0.34f, 0.14f, footBounce, 0f, 0f, 0.14f, 0.10f, 0.18f, belly, 0.25f)
        drawChild(root, sphereMesh, 0.20f, -0.34f, 0.14f, -footBounce, 0f, 0f, 0.14f, 0.10f, 0.18f, belly, 0.25f)

        // Paws
        val pawWave = if (isCelebrating || boopEnergy > 0.3f) {
            52f + sin(elapsedSeconds * 14f) * 22f
        } else {
            sin(elapsedSeconds * 2.8f) * 6f
        }
        drawChild(root, sphereMesh, -0.40f, 0.02f, 0.16f, -pawWave, 0f, 22f, 0.11f, 0.14f, 0.11f, primary, 0.30f)
        drawChild(root, sphereMesh, 0.40f, 0.02f, 0.16f, -pawWave, 0f, -22f, 0.11f, 0.14f, 0.11f, primary, 0.30f)

        // Fluffy cloud tail
        val tailWag = sin(elapsedSeconds * if (isCelebrating) 15f else 5.2f) * 18f
        val tailRoot = FloatArray(16)
        System.arraycopy(root, 0, tailRoot, 0, 16)
        Matrix.translateM(tailRoot, 0, 0f, -0.10f, -0.36f)
        Matrix.rotateM(tailRoot, 0, tailWag, 0f, 1f, 0f)
        drawChild(tailRoot, sphereMesh, 0f, 0.06f, -0.06f, 0f, 0f, 0f, 0.21f, 0.20f, 0.22f, belly, 0.20f)
        drawChild(tailRoot, sphereMesh, -0.08f, 0.18f, -0.14f, 0f, 0f, 0f, 0.16f, 0.16f, 0.16f, belly, 0.20f)
        drawChild(tailRoot, sphereMesh, 0.09f, 0.16f, -0.15f, 0f, 0f, 0f, 0.14f, 0.14f, 0.14f, primary, 0.25f)

        // Expressive head
        val headBob = if (energy < 40) -8f else sin(elapsedSeconds * 2.2f) * 3.5f
        val headRoot = FloatArray(16)
        System.arraycopy(root, 0, headRoot, 0, 16)
        Matrix.translateM(headRoot, 0, 0f, 0.56f, 0.02f)
        Matrix.rotateM(headRoot, 0, headBob, 1f, 0f, 0f)

        drawChild(headRoot, sphereMesh, 0f, 0f, 0f, 0f, 0f, 0f, 0.50f, 0.44f, 0.44f, primary, 0.38f)
        drawChild(headRoot, sphereMesh, 0f, -0.12f, 0.17f, 0f, 0f, 0f, 0.36f, 0.24f, 0.30f, belly, 0.18f)
        drawChild(headRoot, sphereMesh, -0.06f, 0.40f, 0.18f, 0f, 0f, 0f, 0.13f, 0.10f, 0.12f, primary, 0.3f)
        drawChild(headRoot, sphereMesh, 0.07f, 0.38f, 0.20f, 0f, 0f, 0f, 0.10f, 0.09f, 0.10f, primary, 0.3f)

        // Ears
        val earWiggle = sin(elapsedSeconds * 3.6f) * 4f + boopEnergy * 10f
        drawChild(headRoot, coneMesh, -0.31f, 0.38f, 0f, 0f, 0f, 22f + earWiggle, 0.17f, 0.30f, 0.14f, primary, 0.3f)
        drawChild(headRoot, coneMesh, -0.30f, 0.36f, 0.06f, 0f, 0f, 22f + earWiggle, 0.09f, 0.18f, 0.07f, belly, 0.1f)
        drawChild(headRoot, coneMesh, 0.31f, 0.38f, 0f, 0f, 0f, -22f - earWiggle, 0.17f, 0.30f, 0.14f, primary, 0.3f)
        drawChild(headRoot, coneMesh, 0.30f, 0.36f, 0.06f, 0f, 0f, -22f - earWiggle, 0.09f, 0.18f, 0.07f, belly, 0.1f)

        // Rosy cheeks
        drawChild(headRoot, sphereMesh, -0.27f, -0.08f, 0.35f, 0f, 0f, 0f, 0.10f, 0.065f, 0.05f, cheek, 0.15f)
        drawChild(headRoot, sphereMesh, 0.27f, -0.08f, 0.35f, 0f, 0f, 0f, 0.10f, 0.065f, 0.05f, cheek, 0.15f)

        // Eyes
        val blinkCycle = elapsedSeconds % 4.2f
        val isBlinking = blinkCycle > 4.04f
        val eyeHeight = when {
            isCelebrating || boopEnergy > 0.35f -> 0.042f
            isBlinking -> 0.018f
            energy < 35 -> 0.058f
            else -> 0.095f
        }
        drawChild(headRoot, sphereMesh, -0.18f, 0.04f, 0.39f, 0f, 0f, 0f, 0.085f, eyeHeight, 0.05f, darkInk, 0.75f)
        drawChild(headRoot, sphereMesh, 0.18f, 0.04f, 0.39f, 0f, 0f, 0f, 0.085f, eyeHeight, 0.05f, darkInk, 0.75f)
        if (!isBlinking && !isCelebrating && boopEnergy <= 0.35f) {
            drawChild(headRoot, sphereMesh, -0.16f, 0.075f, 0.435f, 0f, 0f, 0f, 0.028f, 0.028f, 0.02f, white, 0.9f)
            drawChild(headRoot, sphereMesh, 0.20f, 0.075f, 0.435f, 0f, 0f, 0f, 0.028f, 0.028f, 0.02f, white, 0.9f)
        }

        // Nose & cute ':3' smile beads
        drawChild(headRoot, sphereMesh, 0f, -0.04f, 0.45f, 0f, 0f, 0f, 0.048f, 0.036f, 0.035f, darkInk, 0.6f)
        drawChild(headRoot, sphereMesh, -0.045f, -0.095f, 0.44f, 0f, 0f, 15f, 0.034f, 0.020f, 0.024f, darkInk, 0.3f)
        drawChild(headRoot, sphereMesh, 0.045f, -0.095f, 0.44f, 0f, 0f, -15f, 0.034f, 0.020f, 0.024f, darkInk, 0.3f)

        drawOutfit(root, headRoot)
    }

    private fun drawOutfit(bodyRoot: FloatArray, headRoot: FloatArray) {
        when (equippedOutfit) {
            PetOutfit.NONE -> Unit
            PetOutfit.BOW -> {
                val bowColor = floatArrayOf(1.0f, 0.42f, 0.62f, 1f)
                val knotColor = floatArrayOf(1.0f, 0.68f, 0.78f, 1f)
                drawChild(headRoot, sphereMesh, 0.20f, 0.43f, 0.14f, 0f, -15f, -20f, 0.11f, 0.07f, 0.06f, bowColor, 0.4f)
                drawChild(headRoot, sphereMesh, 0.06f, 0.45f, 0.16f, 0f, -15f, 20f, 0.11f, 0.07f, 0.06f, bowColor, 0.4f)
                drawChild(headRoot, sphereMesh, 0.13f, 0.44f, 0.16f, 0f, 0f, 0f, 0.055f, 0.055f, 0.055f, knotColor, 0.5f)
            }
            PetOutfit.CROWN -> {
                val gold = floatArrayOf(1.0f, 0.80f, 0.22f, 1f)
                val ruby = floatArrayOf(0.95f, 0.22f, 0.36f, 1f)
                drawChild(headRoot, cylinderMesh, 0f, 0.45f, 0.02f, 0f, 0f, 0f, 0.17f, 0.09f, 0.17f, gold, 0.85f)
                drawChild(headRoot, coneMesh, 0f, 0.54f, 0.13f, 0f, 0f, 0f, 0.05f, 0.11f, 0.05f, gold, 0.85f)
                drawChild(headRoot, coneMesh, -0.12f, 0.53f, 0.02f, 0f, 0f, 8f, 0.05f, 0.10f, 0.05f, gold, 0.85f)
                drawChild(headRoot, coneMesh, 0.12f, 0.53f, 0.02f, 0f, 0f, -8f, 0.05f, 0.10f, 0.05f, gold, 0.85f)
                drawChild(headRoot, sphereMesh, 0f, 0.46f, 0.18f, 0f, 0f, 0f, 0.036f, 0.036f, 0.03f, ruby, 0.9f)
            }
            PetOutfit.HEADPHONES -> {
                val coral = floatArrayOf(0.96f, 0.48f, 0.38f, 1f)
                val cushion = floatArrayOf(0.25f, 0.24f, 0.34f, 1f)
                drawChild(headRoot, torusMesh, 0f, 0.06f, 0f, 0f, 0f, 0f, 0.52f, 0.47f, 0.14f, coral, 0.55f)
                drawChild(headRoot, sphereMesh, -0.50f, 0.02f, 0.02f, 0f, 0f, 0f, 0.10f, 0.17f, 0.17f, coral, 0.55f)
                drawChild(headRoot, sphereMesh, -0.46f, 0.02f, 0.02f, 0f, 0f, 0f, 0.06f, 0.14f, 0.14f, cushion, 0.2f)
                drawChild(headRoot, sphereMesh, 0.50f, 0.02f, 0.02f, 0f, 0f, 0f, 0.10f, 0.17f, 0.17f, coral, 0.55f)
                drawChild(headRoot, sphereMesh, 0.46f, 0.02f, 0.02f, 0f, 0f, 0f, 0.06f, 0.14f, 0.14f, cushion, 0.2f)
            }
            PetOutfit.WIZARD -> {
                val indigo = floatArrayOf(0.32f, 0.26f, 0.72f, 1f)
                val gold = floatArrayOf(1.0f, 0.84f, 0.30f, 1f)
                drawChild(headRoot, cylinderMesh, 0f, 0.41f, 0f, -6f, 0f, 0f, 0.46f, 0.035f, 0.46f, indigo, 0.4f)
                drawChild(headRoot, coneMesh, 0f, 0.66f, -0.02f, -10f, 0f, 0f, 0.26f, 0.44f, 0.26f, indigo, 0.45f)
                drawChild(headRoot, cylinderMesh, 0f, 0.46f, -0.01f, -8f, 0f, 0f, 0.27f, 0.05f, 0.27f, gold, 0.8f)
            }
            PetOutfit.SHADES -> {
                val frame = floatArrayOf(0.12f, 0.12f, 0.18f, 1f)
                val tint = floatArrayOf(0.16f, 0.76f, 0.88f, 0.92f)
                drawChild(headRoot, cubeMesh, -0.17f, 0.05f, 0.43f, 0f, 10f, 0f, 0.14f, 0.09f, 0.03f, frame, 0.8f)
                drawChild(headRoot, cubeMesh, -0.17f, 0.05f, 0.445f, 0f, 10f, 0f, 0.11f, 0.065f, 0.02f, tint, 0.9f)
                drawChild(headRoot, cubeMesh, 0.17f, 0.05f, 0.43f, 0f, -10f, 0f, 0.14f, 0.09f, 0.03f, frame, 0.8f)
                drawChild(headRoot, cubeMesh, 0.17f, 0.05f, 0.445f, 0f, -10f, 0f, 0.11f, 0.065f, 0.02f, tint, 0.9f)
                drawChild(headRoot, cubeMesh, 0f, 0.065f, 0.44f, 0f, 0f, 0f, 0.10f, 0.022f, 0.025f, frame, 0.8f)
            }
            PetOutfit.CAPE -> {
                val crimson = floatArrayOf(0.92f, 0.28f, 0.36f, 1f)
                val gold = floatArrayOf(1.0f, 0.82f, 0.26f, 1f)
                val flutter = sin(elapsedSeconds * 5f) * 5f
                drawChild(bodyRoot, coneMesh, 0f, 0.05f, -0.28f, 22f + flutter, 0f, 0f, 0.42f, 0.56f, 0.16f, crimson, 0.3f)
                drawChild(bodyRoot, sphereMesh, 0f, 0.27f, 0.22f, 0f, 0f, 0f, 0.065f, 0.065f, 0.04f, gold, 0.85f)
            }
        }
    }

    private fun updateAndDrawParticles(dt: Float) {
        synchronized(particleLock) {
            val iter = particles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.life -= dt
                if (p.life <= 0f) {
                    iter.remove()
                    continue
                }
                p.vy -= 3.2f * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.z += p.vz * dt
                p.rot += p.vRot * dt
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawPart(
                    mesh = cubeMesh,
                    tx = p.x, ty = p.y, tz = p.z,
                    rx = p.rot, ry = p.rot * 0.7f, rz = 0f,
                    sx = p.scale, sy = p.scale, sz = p.scale,
                    rgba = floatArrayOf(p.r, p.g, p.b, alpha),
                    specular = 0.6f
                )
            }
        }
    }

    private fun drawChild(
        parent: FloatArray,
        mesh: Mesh,
        tx: Float, ty: Float, tz: Float,
        rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float,
        rgba: FloatArray,
        specular: Float
    ) {
        Matrix.setIdentityM(tempMatrix, 0)
        Matrix.translateM(tempMatrix, 0, tx, ty, tz)
        if (rx != 0f) Matrix.rotateM(tempMatrix, 0, rx, 1f, 0f, 0f)
        if (ry != 0f) Matrix.rotateM(tempMatrix, 0, ry, 0f, 1f, 0f)
        if (rz != 0f) Matrix.rotateM(tempMatrix, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(tempMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(modelMatrix, 0, parent, 0, tempMatrix, 0)
        submitMesh(mesh, rgba, specular)
    }

    private fun drawPart(
        mesh: Mesh,
        tx: Float, ty: Float, tz: Float,
        rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float,
        rgba: FloatArray,
        specular: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, tx, ty, tz)
        if (rx != 0f) Matrix.rotateM(modelMatrix, 0, rx, 1f, 0f, 0f)
        if (ry != 0f) Matrix.rotateM(modelMatrix, 0, ry, 0f, 1f, 0f)
        if (rz != 0f) Matrix.rotateM(modelMatrix, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        submitMesh(mesh, rgba, specular)
    }

    private fun submitMesh(mesh: Mesh, rgba: FloatArray, specular: Float) {
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform4fv(colorHandle, 1, rgba, 0)
        GLES20.glUniform1f(specularHandle, specular)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, mesh.vertexBuffer)

        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 12, mesh.normalBuffer)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, mesh.indexBuffer)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }

    private fun hexToRgba(hex: Long): FloatArray {
        val r = ((hex shr 16) and 0xFF) / 255f
        val g = ((hex shr 8) and 0xFF) / 255f
        val b = (hex and 0xFF) / 255f
        return floatArrayOf(r, g, b, 1f)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private class Mesh(
        vertices: FloatArray,
        normals: FloatArray,
        indices: ShortArray
    ) {
        val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertices)
                position(0)
            }
        val normalBuffer: FloatBuffer = ByteBuffer.allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(normals)
                position(0)
            }
        val indexBuffer: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply {
                put(indices)
                position(0)
            }
        val indexCount: Int = indices.size
    }

    private object MeshBuilder {
        fun createSphere(stacks: Int, slices: Int): Mesh {
            val verts = ArrayList<Float>()
            val norms = ArrayList<Float>()
            val idx = ArrayList<Short>()

            for (i in 0..stacks) {
                val phi = PI * i / stacks
                val sinPhi = sin(phi).toFloat()
                val cosPhi = cos(phi).toFloat()
                for (j in 0..slices) {
                    val theta = 2.0 * PI * j / slices
                    val x = (cos(theta) * sinPhi).toFloat()
                    val y = cosPhi
                    val z = (sin(theta) * sinPhi).toFloat()
                    verts.add(x); verts.add(y); verts.add(z)
                    norms.add(x); norms.add(y); norms.add(z)
                }
            }
            for (i in 0 until stacks) {
                for (j in 0 until slices) {
                    val first = (i * (slices + 1) + j).toShort()
                    val second = (first + slices + 1).toShort()
                    idx.add(first); idx.add(second); idx.add((first + 1).toShort())
                    idx.add(second); idx.add((second + 1).toShort()); idx.add((first + 1).toShort())
                }
            }
            return Mesh(verts.toFloatArray(), norms.toFloatArray(), idx.toShortArray())
        }

        fun createCone(slices: Int): Mesh {
            val verts = ArrayList<Float>()
            val norms = ArrayList<Float>()
            val idx = ArrayList<Short>()

            for (i in 0..slices) {
                val theta = (2.0 * PI * i / slices).toFloat()
                val cosT = cos(theta)
                val sinT = sin(theta)
                verts.add(0f); verts.add(0.5f); verts.add(0f)
                norms.add(cosT * 0.7f); norms.add(0.7f); norms.add(sinT * 0.7f)
                verts.add(cosT * 0.5f); verts.add(-0.5f); verts.add(sinT * 0.5f)
                norms.add(cosT * 0.89f); norms.add(0.45f); norms.add(sinT * 0.89f)
            }
            for (i in 0 until slices) {
                val top = (i * 2).toShort()
                val b1 = (i * 2 + 1).toShort()
                val b2 = ((i + 1) * 2 + 1).toShort()
                idx.add(top); idx.add(b1); idx.add(b2)
            }
            return Mesh(verts.toFloatArray(), norms.toFloatArray(), idx.toShortArray())
        }

        fun createCylinder(slices: Int): Mesh {
            val verts = ArrayList<Float>()
            val norms = ArrayList<Float>()
            val idx = ArrayList<Short>()

            for (i in 0..slices) {
                val theta = (2.0 * PI * i / slices).toFloat()
                val x = cos(theta)
                val z = sin(theta)
                verts.add(x); verts.add(0.5f); verts.add(z)
                norms.add(x); norms.add(0f); norms.add(z)
                verts.add(x); verts.add(-0.5f); verts.add(z)
                norms.add(x); norms.add(0f); norms.add(z)
            }
            for (i in 0 until slices) {
                val t1 = (i * 2).toShort()
                val b1 = (i * 2 + 1).toShort()
                val t2 = ((i + 1) * 2).toShort()
                val b2 = ((i + 1) * 2 + 1).toShort()
                idx.add(t1); idx.add(b1); idx.add(t2)
                idx.add(b1); idx.add(b2); idx.add(t2)
            }
            return Mesh(verts.toFloatArray(), norms.toFloatArray(), idx.toShortArray())
        }

        fun createTorus(majorSegs: Int, minorSegs: Int, minorRadius: Float): Mesh {
            val verts = ArrayList<Float>()
            val norms = ArrayList<Float>()
            val idx = ArrayList<Short>()

            for (i in 0..majorSegs) {
                val u = (PI * i / majorSegs).toFloat()
                val cosU = cos(u)
                val sinU = sin(u)
                for (j in 0..minorSegs) {
                    val v = (2.0 * PI * j / minorSegs).toFloat()
                    val cosV = cos(v)
                    val sinV = sin(v)
                    val r = 1.0f + minorRadius * cosV
                    val x = r * cosU
                    val y = r * sinU
                    val z = minorRadius * sinV
                    verts.add(x); verts.add(y); verts.add(z)
                    val nx = cosV * cosU
                    val ny = cosV * sinU
                    val nz = sinV
                    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
                    norms.add(nx / len); norms.add(ny / len); norms.add(nz / len)
                }
            }
            for (i in 0 until majorSegs) {
                for (j in 0 until minorSegs) {
                    val a = (i * (minorSegs + 1) + j).toShort()
                    val b = ((i + 1) * (minorSegs + 1) + j).toShort()
                    idx.add(a); idx.add(b); idx.add((a + 1).toShort())
                    idx.add(b); idx.add((b + 1).toShort()); idx.add((a + 1).toShort())
                }
            }
            return Mesh(verts.toFloatArray(), norms.toFloatArray(), idx.toShortArray())
        }

        fun createDisc(slices: Int): Mesh {
            val verts = ArrayList<Float>()
            val norms = ArrayList<Float>()
            val idx = ArrayList<Short>()
            verts.add(0f); verts.add(0f); verts.add(0f)
            norms.add(0f); norms.add(1f); norms.add(0f)
            for (i in 0..slices) {
                val theta = (2.0 * PI * i / slices).toFloat()
                verts.add(cos(theta)); verts.add(0f); verts.add(sin(theta))
                norms.add(0f); norms.add(1f); norms.add(0f)
            }
            for (i in 1..slices) {
                idx.add(0.toShort())
                idx.add(i.toShort())
                idx.add((i + 1).toShort())
            }
            return Mesh(verts.toFloatArray(), norms.toFloatArray(), idx.toShortArray())
        }

        fun createCube(): Mesh {
            val s = 0.5f
            val verts = floatArrayOf(
                -s, -s,  s,  s, -s,  s,  s,  s,  s, -s,  s,  s,
                 s, -s, -s, -s, -s, -s, -s,  s, -s,  s,  s, -s,
                -s,  s,  s,  s,  s,  s,  s,  s, -s, -s,  s, -s,
                -s, -s, -s,  s, -s, -s,  s, -s,  s, -s, -s,  s,
                 s, -s,  s,  s, -s, -s,  s,  s, -s,  s,  s,  s,
                -s, -s, -s, -s, -s,  s, -s,  s,  s, -s,  s, -s
            )
            val norms = floatArrayOf(
                 0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,
                 0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,
                 0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,
                 0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f,
                 1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,  1f,  0f,  0f,
                -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f, -1f,  0f,  0f
            )
            val idx = shortArrayOf(
                0, 1, 2, 0, 2, 3,
                4, 5, 6, 4, 6, 7,
                8, 9, 10, 8, 10, 11,
                12, 13, 14, 12, 14, 15,
                16, 17, 18, 16, 18, 19,
                20, 21, 22, 20, 22, 23
            )
            return Mesh(verts, norms, idx)
        }
    }

    private companion object {
        const val VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vFragPos;
            void main() {
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
                vNormal = normalize(mat3(uModelMatrix) * aNormal);
                vFragPos = vec3(uModelMatrix * vec4(aPosition, 1.0));
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLightDir;
            uniform float uAmbient;
            uniform float uSpecular;
            varying vec3 vNormal;
            varying vec3 vFragPos;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 l = normalize(uLightDir);
                float diff = max(dot(n, l), 0.0);
                vec3 viewDir = normalize(vec3(0.0, 0.72, 3.65) - vFragPos);
                vec3 halfDir = normalize(l + viewDir);
                float spec = pow(max(dot(n, halfDir), 0.0), 28.0) * uSpecular;
                float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 3.0) * 0.16;
                vec3 lit = uColor.rgb * (uAmbient + diff * 0.52) + vec3(1.0) * (spec + rim);
                gl_FragColor = vec4(lit, uColor.a);
            }
        """
    }
}
