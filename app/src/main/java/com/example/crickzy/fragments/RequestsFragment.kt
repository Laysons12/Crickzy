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
import com.example.crickzy.adapters.RequestAdapter
import com.example.crickzy.database.SupabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RequestsFragment : Fragment() {

    private lateinit var rvRequests: RecyclerView
    private lateinit var tvEmptyState: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_requests, container, false)
        
        rvRequests = view.findViewById(R.id.rvRequests)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        rvRequests.layoutManager = LinearLayoutManager(context)

        return view
    }

    override fun onResume() {
        super.onResume()
        loadRequests()
    }

    private fun loadRequests() {
        viewLifecycleOwner.lifecycleScope.launch {
            val requests = withContext(Dispatchers.IO) { 
                SupabaseHelper.getAllRequests() 
            }
            
            if (requests.isEmpty()) {
                tvEmptyState.visibility = View.VISIBLE
                rvRequests.visibility = View.GONE
            } else {
                tvEmptyState.visibility = View.GONE
                rvRequests.visibility = View.VISIBLE
                rvRequests.adapter = RequestAdapter(requests)
            }
        }
    }
}
