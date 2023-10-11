package com.boolder.boolder.view.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import coil.load
import com.boolder.boolder.R
import com.boolder.boolder.databinding.ViewTopoBinding
import com.boolder.boolder.domain.model.CircuitInfo
import com.boolder.boolder.domain.model.CompleteProblem
import com.boolder.boolder.domain.model.Problem
import com.boolder.boolder.domain.model.ProblemWithLine
import com.boolder.boolder.domain.model.Steepness
import com.boolder.boolder.domain.model.Topo
import com.boolder.boolder.domain.model.toUiProblem
import com.boolder.boolder.view.compose.BoolderTheme
import com.boolder.boolder.view.detail.composable.CircuitControls
import com.boolder.boolder.view.detail.composable.ProblemStartsLayer
import com.boolder.boolder.view.detail.uimodel.UiProblem
import java.util.Locale

class TopoView(
    context: Context,
    attrs: AttributeSet?
) : ConstraintLayout(context, attrs) {

    private val binding = ViewTopoBinding.inflate(LayoutInflater.from(context), this)

    private var uiProblems: List<UiProblem> = emptyList()

    var onSelectProblemOnMap: ((problemId: String) -> Unit)? = null
    var onCircuitProblemSelected: ((problemId: Int) -> Unit)? = null

    fun setTopo(topo: Topo) {
        setUiProblems(
            uiProblems = emptyList(),
            selectedProblem = null
        )

        loadTopoImage(topo)

        updateCircuitControls(circuitInfo = topo.circuitInfo)

        topo.selectedCompleteProblem?.let {
            updateLabels(it.problemWithLine.problem)
            setupChipClick(it.problemWithLine.problem)
        }
    }

    private fun onProblemPictureLoaded(topo: Topo) {
        val selectedProblem = topo.selectedCompleteProblem ?: return

        val containerWidth = binding.picture.measuredWidth
        val containerHeight = binding.picture.measuredHeight

        val initialUiProblem = selectedProblem.toUiProblem(
            containerWidthPx = containerWidth,
            containerHeightPx = containerHeight
        )

        val otherUiProblems = topo.otherCompleteProblems.map {
            it.toUiProblem(
                containerWidthPx = containerWidth,
                containerHeightPx = containerHeight
            )
        }

        this.uiProblems = otherUiProblems + initialUiProblem

        setUiProblems(
            uiProblems = uiProblems,
            selectedProblem = selectedProblem
        )
    }

    private fun onProblemStartClicked(completeProblem: CompleteProblem) {
        val problem = completeProblem.problemWithLine.problem

        updateLabels(problem)
        setupChipClick(problem)
        setUiProblems(
            uiProblems = uiProblems,
            selectedProblem = completeProblem
        )
        onSelectProblemOnMap?.invoke(problem.id.toString())
    }

    private fun onVariantSelected(variant: ProblemWithLine) {
        val (selectedProblem, newUiProblems) = VariantSelector.selectVariantInProblemStarts(
            selectedVariant = variant,
            uiProblems = uiProblems,
            containerWidth = binding.picture.measuredWidth,
            containerHeight = binding.picture.measuredHeight
        )

        uiProblems = newUiProblems

        selectedProblem?.let {
            updateLabels(it.problemWithLine.problem)
            setupChipClick(it.problemWithLine.problem)
        }

        setUiProblems(
            uiProblems = uiProblems,
            selectedProblem = selectedProblem
        )

        selectedProblem?.problemWithLine?.problem?.id?.toString()?.let { selectedProblemId ->
            onSelectProblemOnMap?.invoke(selectedProblemId)
        }
    }

    private fun updateCircuitControls(circuitInfo: CircuitInfo?) {
        binding.circuitControlsComposeView.setContent {
            circuitInfo ?: return@setContent

            BoolderTheme {
                CircuitControls(
                    circuitInfo = circuitInfo,
                    onPreviousProblemClicked = {
                        circuitInfo.previousProblemId?.let { onCircuitProblemSelected?.invoke(it) }
                    },
                    onNextProblemClicked = {
                        circuitInfo.nextProblemId?.let { onCircuitProblemSelected?.invoke(it) }
                    }
                )
            }
        }
    }

    private fun updateLabels(problem: Problem) {
        binding.title.text = problem.nameSafe()
        binding.grade.text = problem.grade

        val steepness = Steepness.fromTextValue(problem.steepness)

        binding.typeIcon.apply {
            val steepnessDrawable = steepness.iconRes
                ?.let { ContextCompat.getDrawable(context, it) }

            setImageDrawable(steepnessDrawable)
            isVisible = steepnessDrawable != null
        }

        binding.typeText.apply {
            val steepnessText = steepness.textRes?.let(context::getString)

            val sitStartText = if (problem.sitStart) {
                resources.getString(R.string.sit_start)
            } else null

            text = listOfNotNull(steepnessText, sitStartText).joinToString(separator = " • ")
            isVisible = !text.isNullOrEmpty()
        }
    }

    private fun setupChipClick(problem: Problem) {
        binding.bleauInfo.isVisible = !problem.bleauInfoId.isNullOrEmpty()
        binding.bleauInfo.setOnClickListener {
            try {
                val bleauUrl = "https://bleau.info/a/${problem.bleauInfoId}.html"
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(bleauUrl))
                context.startActivity(browserIntent)
            } catch (e: Exception) {
                Log.i("Bottom Sheet", "No apps can handle this kind of intent")
            }
        }

        binding.share.setOnClickListener {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    "https://www.boolder.com/${Locale.getDefault().language}/p/${problem.id}"
                )
                type = "text/plain"
            }

            try {
                val shareIntent = Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            } catch (e: Exception) {
                Log.i("Bottom Sheet", "No apps can handle this kind of intent")
            }
        }
    }

    private fun loadTopoImage(topo: Topo) {
        binding.progressCircular.isVisible = true

        if (topo.pictureUrl == null) {
            loadErrorPicture()
            return
        }

        binding.picture.load(topo.pictureUrl) {
            crossfade(true)
            error(R.drawable.ic_placeholder)

            listener(
                onSuccess = { _, _ ->
                    context?.let {
                        binding.picture.setPadding(0)
                        binding.progressCircular.isVisible = false
                        onProblemPictureLoaded(topo)
                    }
                },
                onError = { _, _ -> loadErrorPicture() }
            )
        }
    }

    private fun loadErrorPicture() {
        binding.picture.setImageDrawable(
            ContextCompat.getDrawable(context, R.drawable.ic_placeholder)
        )
        binding.picture.setPadding(200)
        binding.progressCircular.isVisible = false
    }

    private fun setUiProblems(
        uiProblems: List<UiProblem>,
        selectedProblem: CompleteProblem?
    ) {
        binding.problemStartsComposeView.setContent {
            BoolderTheme {
                ProblemStartsLayer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    uiProblems = uiProblems,
                    selectedProblem = selectedProblem,
                    onProblemStartClicked = ::onProblemStartClicked,
                    onVariantSelected = ::onVariantSelected
                )
            }
        }
    }

    private fun Problem.nameSafe(): String =
        if (Locale.getDefault().language == "fr") {
            name.orEmpty()
        } else {
            nameEn.orEmpty()
        }
}
