package com.example.crickzy.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.crickzy.R
import com.example.crickzy.adapters.TurfAdapter
import com.example.crickzy.database.SupabaseHelper
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TurfBookingFragment : Fragment() {

    private lateinit var rvTurfs: RecyclerView
    private lateinit var tvEmptyState: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_turf_booking, container, false)
        
        rvTurfs = view.findViewById(R.id.rvTurfs)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        rvTurfs.layoutManager = LinearLayoutManager(context)

        val fabAddTurf = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAddTurf)
        fabAddTurf.setOnClickListener {
            startActivity(android.content.Intent(activity, com.example.crickzy.activities.AddTurfActivity::class.java))
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        loadTurfs()
    }

    private fun loadTurfs() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { SupabaseHelper.insertDummyTurfs() }
            val turfs = withContext(Dispatchers.IO) { SupabaseHelper.getAllTurfs() }
            val prefs = requireContext().getSharedPreferences("CrickzyPrefs", android.content.Context.MODE_PRIVATE)
            val currentUserId = prefs.getLong("USER_ID", -1L)

            if (turfs.isEmpty()) {
                tvEmptyState.visibility = View.VISIBLE
                rvTurfs.visibility = View.GONE
            } else {
                tvEmptyState.visibility = View.GONE
                rvTurfs.visibility = View.VISIBLE
                rvTurfs.adapter = TurfAdapter(turfs, currentUserId, { turfToDelete ->
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete Turf")
                        .setMessage("Are you sure you want to delete '${turfToDelete.name}'?")
                        .setPositiveButton("Delete") { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val success = withContext(Dispatchers.IO) { SupabaseHelper.deleteTurf(turfToDelete.id) }
                                if (success) {
                                    Toast.makeText(requireContext(), "Turf deleted", Toast.LENGTH_SHORT).show()
                                    loadTurfs() // reload
                                } else {
                                    Toast.makeText(requireContext(), "Failed to delete turf", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }, { turfToEdit ->
                    val dialogView = layoutInflater.inflate(R.layout.activity_add_turf, null)
                    val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTurfName)
                    val etLocation = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTurfLocation)
                    val etPrice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTurfPrice)
                    val btnSave = dialogView.findViewById<android.widget.Button>(R.id.btnSubmitTurf)
                    
                    if (btnSave != null) btnSave.visibility = View.GONE
                    
                    etName.setText(turfToEdit.name)
                    etLocation.setText(turfToEdit.location)
                    etPrice.setText(turfToEdit.pricePerHour.toString())

                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Edit Turf")
                        .setView(dialogView)
                        .setPositiveButton("Save") { _, _ ->
                            val newName = etName.text.toString().trim()
                            val newLoc = etLocation.text.toString().trim()
                            val newPrice = etPrice.text.toString().trim().toDoubleOrNull() ?: 0.0
                            if (newName.isNotEmpty() && newLoc.isNotEmpty() && newPrice > 0) {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    val success = withContext(Dispatchers.IO) { SupabaseHelper.updateTurf(turfToEdit.id, newName, newLoc, newPrice) }
                                    if (success) {
                                        Toast.makeText(requireContext(), "Turf updated", Toast.LENGTH_SHORT).show()
                                        loadTurfs()
                                    } else {
                                        Toast.makeText(requireContext(), "Update failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                })
            }
        }
    }
}
