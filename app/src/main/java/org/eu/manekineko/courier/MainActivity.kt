package org.eu.manekineko.courier

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import org.eu.manekineko.courier.data.database.TrackDatabase
import org.eu.manekineko.courier.data.model.TrackEntity
import org.eu.manekineko.courier.databinding.ActivityMainBinding
import org.eu.manekineko.courier.ui.map.MapFragment
import org.eu.manekineko.courier.ui.profile.ProfileFragment
import org.eu.manekineko.courier.ui.tracks.TracksFragment
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.osmdroid.util.GeoPoint

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var mapFragment: MapFragment
    private lateinit var profileFragment: ProfileFragment
    private lateinit var tracksFragment: TracksFragment
    private var activeFragment: Fragment? = null

    private val database by lazy { TrackDatabase.getInstance(this) }
    private val trackDao by lazy { database.trackDao() }

    companion object {
        private const val TAG_MAP = "fragment_map"
        private const val TAG_PROFILE = "fragment_profile"
        private const val TAG_TRACKS = "fragment_tracks"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mapFragment = MapFragment()
        profileFragment = ProfileFragment()
        tracksFragment = TracksFragment()

        setupBottomNavigation()
        showFragment(mapFragment, TAG_MAP)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_map -> showFragment(mapFragment, TAG_MAP)
                R.id.navigation_tracks -> showFragment(tracksFragment, TAG_TRACKS)
                R.id.navigation_profile -> showFragment(profileFragment, TAG_PROFILE)
            }
            true
        }
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        if (activeFragment == fragment) return

        supportFragmentManager.beginTransaction()
            .apply {
                activeFragment?.let { hide(it) }
                if (!fragment.isAdded) {
                    add(R.id.fragmentContainer, fragment, tag)
                }
                show(fragment)
            }
            .commit()

        activeFragment = fragment
    }
}
