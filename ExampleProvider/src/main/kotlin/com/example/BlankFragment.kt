package com.example

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute

/**
 * A simple [Fragment] subclass.
 */
class BlankFragment(private val plugin: ExamplePlugin) : BottomSheetDialogFragment() {

    // Helper function to get a drawable resource by name
    @SuppressLint("DiscouragedApi")
    @Suppress("SameParameterValue")
    private fun getDrawable(name: String): Drawable? {
        val id = plugin.resources?.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return id?.let { ResourcesCompat.getDrawable(plugin.resources ?: return null, it, null) }
    }

    // Helper function to get a string resource by name
    @SuppressLint("DiscouragedApi")
    @Suppress("SameParameterValue")
    private fun getString(name: String): String? {
        val id = plugin.resources?.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return id?.let { plugin.resources?.getString(it) }
    }

    // Generic findView function to find views by name
    @SuppressLint("DiscouragedApi")
    private fun <T : View> View.findViewByName(name: String): T? {
        val id = plugin.resources?.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return findViewById(id ?: return null)
    }

    @SuppressLint("DiscouragedApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val layoutId = plugin.resources?.getIdentifier("fragment_blank", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        return layoutId?.let {
            inflater.inflate(plugin.resources?.getLayout(it), container, false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        val imageView: ImageView? = view.findViewByName("imageView")
        val imageView2: ImageView? = view.findViewByName("imageView2")
        val textView: TextView? = view.findViewByName("textView")
        val textView2: TextView? = view.findViewByName("textView2")

        // Set text and styling if the views are found
        textView?.apply {
            text = getString("hello_fragment")
            TextViewCompat.setTextAppearance(this, R.style.ResultInfoText)
        }

        textView2?.text = view.context.resources.getText(R.string.legal_notice_text)

        // Set image resources and tint if the views are found
        imageView?.apply {
            setImageDrawable(getDrawable("ic_android_24dp"))
            imageTintList = ColorStateList.valueOf(view.context.getColor(R.color.white))
        }

        imageView2?.apply {
            setImageDrawable(getDrawable("ic_android_24dp"))
            imageTintList = ColorStateList.valueOf(view.context.colorFromAttribute(R.attr.white))
        }
    }
}