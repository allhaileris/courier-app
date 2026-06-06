package org.eu.manekineko.courier.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import org.eu.manekineko.courier.data.database.TrackDatabase
import org.eu.manekineko.courier.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val trackDao by lazy {
        TrackDatabase.getInstance(requireContext()).trackDao()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeStats()
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                trackDao.getAllTracks().collect { tracks ->
                    binding.deliveriesValue.text = tracks.size.toString()

                    var totalPoints = 0
                    tracks.forEach { track ->
                        try {
                            val arr = org.json.JSONArray(track.pointsJson)
                            totalPoints += arr.length()
                        } catch (e: Exception) { /* skip */ }
                    }
                    binding.totalPointsValue.text = totalPoints.toString()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
