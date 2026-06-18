package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.PlaceAdapter
import com.example.myapplication.data.Place
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.databinding.DialogAddPlaceBinding
import com.example.myapplication.viewmodel.PlaceViewModel
import com.example.myapplication.viewmodel.PlaceViewModelFactory
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private val viewModel: PlaceViewModel by viewModels {
        PlaceViewModelFactory((application as MyApplication).repository)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {

        } else {
            Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraTopGap = (32 * resources.displayMetrics.density).toInt()
            binding.appBarLayout.updatePadding(top = systemBars.top + extraTopGap)
            binding.appBarLayout.updatePadding(bottom = (16 * resources.displayMetrics.density).toInt())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val adapter = PlaceAdapter { place ->
            showEditDeleteDialog(place)
        }
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.allPlaces.observe(this) { places ->
            adapter.submitList(places)
            if (places.isEmpty()) {
                binding.recyclerView.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
            } else {
                binding.recyclerView.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
            }
        }

        binding.fabAdd.setOnClickListener {
            showAddPlaceDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_all -> {
                showDeleteAllConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_all)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton("Sim") { _, _ ->
                viewModel.deleteAll()
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun showAddPlaceDialog() {
        val dialogBinding = DialogAddPlaceBinding.inflate(layoutInflater)
        setupUseCurrentLocationButton(dialogBinding)

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.add_place)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
        
        val dialog = builder.create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val name = dialogBinding.etName.text.toString().trim()
                val description = dialogBinding.etDescription.text.toString().trim()
                val address = dialogBinding.etAddress.text.toString().trim()
                val locality = dialogBinding.etLocality.text.toString().trim()

                if (name.isEmpty()) {
                    dialogBinding.etName.error = getString(R.string.name_required)
                    return@setOnClickListener
                }

                val coordinates = getOptionalCoordinates(dialogBinding) ?: return@setOnClickListener
                val place = Place(
                    name = name,
                    description = description,
                    address = address,
                    locality = locality,
                    latitude = coordinates.first,
                    longitude = coordinates.second
                )
                viewModel.insert(place)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showEditDeleteDialog(place: Place) {
        val options = arrayOf(
            getString(R.string.edit),
            getString(R.string.delete_place),
            getString(R.string.share),
            getString(R.string.view_on_map)
        )
        AlertDialog.Builder(this)
            .setTitle(place.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditPlaceDialog(place)
                    1 -> viewModel.delete(place)
                    2 -> sharePlace(place)
                    3 -> openMap(place)
                }
            }
            .show()
    }

    private fun showEditPlaceDialog(place: Place) {
        val dialogBinding = DialogAddPlaceBinding.inflate(layoutInflater)
        dialogBinding.etName.setText(place.name)
        dialogBinding.etDescription.setText(place.description)
        dialogBinding.etAddress.setText(place.address)
        dialogBinding.etLocality.setText(place.locality)
        dialogBinding.etLatitude.setText(place.latitude?.toString().orEmpty())
        dialogBinding.etLongitude.setText(place.longitude?.toString().orEmpty())
        setupUseCurrentLocationButton(dialogBinding)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.edit_place)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.update, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val name = dialogBinding.etName.text.toString().trim()

                if (name.isEmpty()) {
                    dialogBinding.etName.error = getString(R.string.name_required)
                    return@setOnClickListener
                }

                val coordinates = getOptionalCoordinates(dialogBinding) ?: return@setOnClickListener
                val updatedPlace = place.copy(
                    name = name,
                    description = dialogBinding.etDescription.text.toString().trim(),
                    address = dialogBinding.etAddress.text.toString().trim(),
                    locality = dialogBinding.etLocality.text.toString().trim(),
                    latitude = coordinates.first,
                    longitude = coordinates.second
                )
                viewModel.update(updatedPlace)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun setupUseCurrentLocationButton(dialogBinding: DialogAddPlaceBinding) {
        fun updateButtonState() {
            dialogBinding.btnUseCurrentLocation.isEnabled =
                dialogBinding.etName.text.toString().trim().isNotEmpty()
        }

        updateButtonState()
        dialogBinding.etName.doAfterTextChanged {
            updateButtonState()
        }

        dialogBinding.btnUseCurrentLocation.setOnClickListener {
            if (!hasLocationPermission()) {
                requestLocationPermissions()
                return@setOnClickListener
            }

            getCurrentLocation { location ->
                if (location == null) {
                    Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_SHORT).show()
                    return@getCurrentLocation
                }

                dialogBinding.etLatitude.setText(location.latitude.toString())
                dialogBinding.etLongitude.setText(location.longitude.toString())
            }
        }
    }

    private fun getOptionalCoordinates(dialogBinding: DialogAddPlaceBinding): Pair<Double?, Double?>? {
        dialogBinding.tilLatitude.error = null
        dialogBinding.tilLongitude.error = null

        val latitudeText = dialogBinding.etLatitude.text.toString().trim()
        val longitudeText = dialogBinding.etLongitude.text.toString().trim()

        if (latitudeText.isEmpty() && longitudeText.isEmpty()) {
            return null to null
        }

        val latitude = latitudeText.toDoubleOrNull()
        val longitude = longitudeText.toDoubleOrNull()

        var isValid = true
        if (latitude == null || latitude !in -90.0..90.0) {
            dialogBinding.tilLatitude.error = getString(R.string.invalid_latitude)
            isValid = false
        }
        if (longitude == null || longitude !in -180.0..180.0) {
            dialogBinding.tilLongitude.error = getString(R.string.invalid_longitude)
            isValid = false
        }

        if (!isValid || latitude == null || longitude == null) {
            return null
        }
        return latitude to longitude
    }

    private fun sharePlace(place: Place) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, place.name)
            val text = if (place.latitude != null && place.longitude != null) {
                getString(R.string.share_text_with_coordinates, place.name, place.description, place.latitude, place.longitude)
            } else {
                getString(R.string.share_text_without_coordinates, place.name, place.description, Uri.encode(getMapSearchQuery(place)))
            }
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
    }

    private fun openMap(place: Place) {
        val gmmIntentUri = if (place.latitude != null && place.longitude != null) {
            Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(place.name)})")
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(getMapSearchQuery(place))}")
        }
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
        }
    }

    private fun getMapSearchQuery(place: Place): String {
        val address = place.address.trim()
        val locality = place.locality.trim()

        return when {
            address.isNotEmpty() && locality.isNotEmpty() -> "$address, $locality"
            address.isNotEmpty() -> address
            locality.isNotEmpty() -> "${place.name}, $locality"
            else -> place.name
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        return fineLocation == PackageManager.PERMISSION_GRANTED ||
            coarseLocation == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun getCurrentLocation(onLocationReceived: (Location?) -> Unit) {
        if (hasLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                onLocationReceived(location)
            }.addOnFailureListener {
                onLocationReceived(null)
            }
        } else {
            onLocationReceived(null)
        }
    }
}
