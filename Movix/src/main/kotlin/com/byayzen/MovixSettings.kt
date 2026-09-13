package com.byayzen

import android.R
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.MainActivity
import org.json.JSONArray

object MovixSettings {
    private const val PREFS_PREFIX = "MOVIX_CAT_"

    data class CategoryItem(
        val key: String,
        val title: String,
        val isDefaultEnabled: Boolean
    )

    val allCategories = listOf(
        CategoryItem("movie/now_playing", "Nouveaux Films", true),
        CategoryItem("tv/on_the_air", "Nouvelles Séries", true),
        CategoryItem("discover/movie?with_watch_providers=8&watch_region=FR", "Netflix Films", true),
        CategoryItem("discover/tv?with_watch_providers=8&watch_region=FR", "Netflix Séries", true),
        CategoryItem("discover/movie?with_watch_providers=119&watch_region=FR", "Prime Video Films", true),
        CategoryItem("discover/tv?with_watch_providers=119&watch_region=FR", "Prime Video Séries", true),
        CategoryItem("discover/movie?with_watch_providers=337&watch_region=FR", "Disney+ Films", true),
        CategoryItem("discover/tv?with_watch_providers=337&watch_region=FR", "Disney+ Séries", true),
        CategoryItem("tv/16", "Anime", true),
        CategoryItem("movie/28", "Action", true),
        CategoryItem("movie/12", "Aventure", true),
        CategoryItem("movie/16", "Animation", true),
        CategoryItem("movie/35", "Comédie", true),
        CategoryItem("movie/80", "Crime", true),
        CategoryItem("movie/99", "Documentaire", true),
        CategoryItem("movie/18", "Drame", true),
        CategoryItem("movie/10751", "Famille", true),
        CategoryItem("movie/14", "Fantastique", true),
        CategoryItem("movie/36", "Histoire", true),
        CategoryItem("movie/27", "Horreur", true),
        CategoryItem("movie/9648", "Mystère", true),
        CategoryItem("movie/10749", "Romance", true),
        CategoryItem("movie/878", "Science-Fiction", true),
        CategoryItem("movie/53", "Thriller", true),
        CategoryItem("movie/10752", "Guerre", true),
        CategoryItem("tv/10759", "Action et Aventure", true),
        CategoryItem("tv/35", "Comédie TV", true),
        CategoryItem("tv/80", "Crime TV", true),
        CategoryItem("tv/18", "Drame TV", true),
        CategoryItem("tv/10751", "Famille TV", true),
        CategoryItem("tv/10762", "Enfants", true),
        CategoryItem("tv/9648", "Mystère TV", true),
        CategoryItem("tv/10763", "Actualités", true),
        CategoryItem("tv/10764", "Téléréalité", true),
        CategoryItem("livetv/catalog/tv/northlive_sport", "Live TV - Sports", true),
        CategoryItem("livetv/catalog/tv/vavoo_france", "Live TV - France", true),
        CategoryItem("discover/tv?with_genres=16&sort_by=popularity.desc&vote_count.gte=25&with_keywords=210024", "Animes Populaires", false),
        CategoryItem("discover/tv?with_genres=16,10765&sort_by=popularity.desc&vote_count.gte=25&with_keywords=210024", "Animes Sci-Fi & Fantastique", false),
        CategoryItem("discover/tv?with_genres=16,10759&sort_by=popularity.desc&vote_count.gte=25&with_keywords=210024", "Animes Action & Aventure", false),
        CategoryItem("discover/tv?with_genres=16,35&sort_by=popularity.desc&vote_count.gte=25&with_keywords=210024", "Animes Comédie", false),
        CategoryItem("discover/tv?with_genres=16,18&sort_by=popularity.desc&vote_count.gte=25&with_keywords=210024", "Animes Drame", false),
        CategoryItem("discover/tv?with_genres=16,9648&sort_by=popularity.desc&vote_count.gte=25&with_keywords=210024", "Animes Mystère", false),
        CategoryItem("discover/tv?with_genres=16,10751&sort_by=popularity.desc&vote_count.gte=25&with_keywords=210024", "Animes Famille", false),
        CategoryItem("discover/tv?with_genres=16,10762&sort_by=popularity.desc&vote_count.gte=25&with_keywords=210024", "Animes Enfants", false),
        CategoryItem("trending/tv/day", "Séries Tendances", false),
        CategoryItem("discover/tv?with_genres=10768", "Séries Guerre & Politique", false),
        CategoryItem("discover/tv?with_genres=10767", "Talk Shows TV", false),
        CategoryItem("discover/tv?with_genres=10766", "Soap Operas", false),
        CategoryItem("discover/tv?with_genres=10765", "Séries Sci-Fi & Fantastique", false),
        CategoryItem("discover/tv?with_genres=10759|18|10768&vote_average.gte=7&vote_count.gte=100", "Top Séries Notées", false)
    )

    private fun getLocalizedString(context: Context, key: String): String {
        val lang = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0].language
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale.language
            }
        } catch (_: Exception) { "en" }

        return when (key) {
            "title" -> when (lang) {
                "fr" -> "Catégories"
                "tr" -> "Kategoriler"
                else -> "Categories"
            }
            "save" -> when (lang) {
                "fr" -> "Enregistrer"
                "tr" -> "Kaydet"
                else -> "Save"
            }
            "close" -> when (lang) {
                "fr" -> "Fermer"
                "tr" -> "Kapat"
                else -> "Close"
            }
            else -> key
        }
    }

    fun isCategoryEnabled(catKey: String, isDefaultEnabled: Boolean): Boolean {
        return getKey<Boolean>("$PREFS_PREFIX$catKey") ?: isDefaultEnabled
    }

    fun setCategoryEnabled(catKey: String, enabled: Boolean) {
        setKey("$PREFS_PREFIX$catKey", enabled)
    }

    fun getOrderedCategories(): List<String> {
        val defaultList = allCategories.map { it.key }
        val categoryMap = allCategories.associateBy { it.key }
        val savedJson = getKey<String>("MOVIX_CAT_ORDER")
        val baseList = if (savedJson.isNullOrBlank()) {
            defaultList
        } else {
            try {
                val jsonArray = JSONArray(savedJson)
                val savedList = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                val missingItems = defaultList.filter { it !in savedList }
                savedList + missingItems
            } catch (_: Exception) {
                defaultList
            }
        }

        val (enabledList, disabledList) = baseList.partition { key ->
            val cat = categoryMap[key]
            isCategoryEnabled(key, cat?.isDefaultEnabled ?: false)
        }
        return enabledList + disabledList
    }

    fun setOrderedCategories(list: List<String>) {
        val jsonArray = JSONArray().apply { list.forEach { put(it) } }
        setKey("MOVIX_CAT_ORDER", jsonArray.toString())
    }

    fun showSettingsDialog(context: Context, pluginResources: Resources? = null) {
        try {
            val pluginPackage = "com.byayzen"
            val res = pluginResources ?: context.resources

            var layoutId = try { res.getIdentifier("movix_settings", "layout", pluginPackage) } catch (_: Exception) { 0 }
            if (layoutId == 0) {
                layoutId = try { context.resources.getIdentifier("movix_settings", "layout", pluginPackage) } catch (_: Exception) { 0 }
            }

            val layoutInflater = LayoutInflater.from(context)
            val view = if (layoutId != 0) {
                try {
                    layoutInflater.inflate(res.getLayout(layoutId), null)
                } catch (_: Exception) {
                    try { layoutInflater.inflate(layoutId, null) } catch (_: Exception) { null }
                }
            } else null

            var recyclerView: RecyclerView? = null
            if (view != null) {
                var recyclerId = try { res.getIdentifier("recycler_categories", "id", pluginPackage) } catch (_: Exception) { 0 }
                if (recyclerId == 0) recyclerId = try { context.resources.getIdentifier("recycler_categories", "id", context.packageName) } catch (_: Exception) { 0 }
                if (recyclerId != 0) {
                    recyclerView = view.findViewById(recyclerId)
                }
            }

            val fallbackPair = if (view == null || recyclerView == null) buildFallbackView(context) else null
            val finalView = view ?: fallbackPair?.first
            if (fallbackPair != null) {
                recyclerView = fallbackPair.second
            }

            val currentOrderedKeys = getOrderedCategories().toMutableList()

            recyclerView?.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = CategoryAdapter(currentOrderedKeys, this)
                isFocusable = true
                isFocusableInTouchMode = true
            }

            AlertDialog.Builder(context)
                .setTitle(getLocalizedString(context, "title"))
                .setView(finalView)
                .setPositiveButton(getLocalizedString(context, "save")) { d, _ ->
                    d.dismiss()
                    try {
                        MainActivity.reloadHomeEvent.invoke(true)
                    } catch (_: Exception) {}
                }
                .setNegativeButton(getLocalizedString(context, "close")) { d, _ ->
                    d.dismiss()
                }
                .create()
                .show()
        } catch (_: Exception) {}
    }

    private fun buildFallbackView(context: Context): Pair<View, RecyclerView> {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 10, 20, 10)
        }
        val rv = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        layout.addView(rv)
        return Pair(layout, rv)
    }

    private class CategoryAdapter(
        private val orderedKeys: MutableList<String>,
        private val recyclerView: RecyclerView
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        private val categoryMap = allCategories.associateBy { it.key }
        private val cbId = View.generateViewId()
        private val downId = View.generateViewId()
        private val upId = View.generateViewId()

        class ViewHolder(container: LinearLayout, cbId: Int, downId: Int, upId: Int) : RecyclerView.ViewHolder(container) {
            val checkBox: CheckBox = container.findViewById(cbId)
            val downButton: Button = container.findViewById(downId)
            val upButton: Button = container.findViewById(upId)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val focusColor = Color.parseColor("#00D9FF")
            val checkedColor = Color.parseColor("#FFC107")

            val btnSize = (32 * context.resources.displayMetrics.density).toInt()
            val btnMargin = (6 * context.resources.displayMetrics.density).toInt()

            val circleFocusDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(focusColor)
            }

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 12, 16, 12)
                isFocusable = false

                val cb = CheckBox(context).apply {
                    id = cbId
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    isFocusable = true
                    isFocusableInTouchMode = true

                    buttonTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(R.attr.state_focused, R.attr.state_checked),
                            intArrayOf(R.attr.state_focused, -R.attr.state_checked),
                            intArrayOf(-R.attr.state_checked),
                            intArrayOf(R.attr.state_checked)
                        ),
                        intArrayOf(focusColor, focusColor, Color.GRAY, checkedColor)
                    )

                    setOnFocusChangeListener { _, hasFocus ->
                        setTextColor(if (hasFocus) focusColor else Color.WHITE)
                    }
                }

                val btnContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isFocusable = false

                    val btnDown = Button(context).apply {
                        id = downId
                        text = "▼"
                        textSize = 12f
                        layoutParams = LinearLayout.LayoutParams(btnSize, btnSize).apply { marginEnd = btnMargin }
                        setBackgroundColor(Color.TRANSPARENT)
                        setTextColor(Color.WHITE)
                        isFocusable = true
                        isFocusableInTouchMode = true

                        setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus && isEnabled) {
                                background = circleFocusDrawable
                                setTextColor(Color.BLACK)
                            } else {
                                setBackgroundColor(Color.TRANSPARENT)
                                setTextColor(if (isEnabled) Color.WHITE else Color.GRAY)
                            }
                        }
                    }

                    val btnUp = Button(context).apply {
                        id = upId
                        text = "▲"
                        textSize = 12f
                        layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                        setBackgroundColor(Color.TRANSPARENT)
                        setTextColor(Color.WHITE)
                        isFocusable = true
                        isFocusableInTouchMode = true

                        setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus && isEnabled) {
                                background = circleFocusDrawable
                                setTextColor(Color.BLACK)
                            } else {
                                setBackgroundColor(Color.TRANSPARENT)
                                setTextColor(if (isEnabled) Color.WHITE else Color.GRAY)
                            }
                        }
                    }

                    addView(btnDown)
                    addView(btnUp)
                }

                addView(cb)
                addView(btnContainer)
            }
            return ViewHolder(layout, cbId, downId, upId)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val key = orderedKeys[position]
            val cat = categoryMap[key] ?: return

            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.text = cat.title
            holder.checkBox.isChecked = isCategoryEnabled(cat.key, cat.isDefaultEnabled)

            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                setCategoryEnabled(cat.key, isChecked)
                recyclerView.post {
                    val reordered = getOrderedCategories().toMutableList()
                    orderedKeys.clear()
                    orderedKeys.addAll(reordered)
                    setOrderedCategories(orderedKeys)
                    notifyDataSetChanged()
                }
            }

            holder.upButton.isEnabled = position > 0
            holder.downButton.isEnabled = position < orderedKeys.size - 1

            holder.upButton.setTextColor(if (holder.upButton.isEnabled) Color.WHITE else Color.GRAY)
            holder.downButton.setTextColor(if (holder.downButton.isEnabled) Color.WHITE else Color.GRAY)

            holder.upButton.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos > 0) {
                    moveItem(pos, pos - 1, true)
                }
            }

            holder.downButton.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0 && pos < orderedKeys.size - 1) {
                    moveItem(pos, pos + 1, false)
                }
            }
        }

        override fun getItemCount(): Int = orderedKeys.size

        private fun moveItem(from: Int, to: Int, isUpButton: Boolean) {
            if (from < 0 || from >= orderedKeys.size || to < 0 || to >= orderedKeys.size) return

            val key = orderedKeys.removeAt(from)
            orderedKeys.add(to, key)
            setOrderedCategories(orderedKeys)

            notifyItemMoved(from, to)
            notifyItemChanged(from)
            notifyItemChanged(to)

            recyclerView.post {
                val holder = recyclerView.findViewHolderForAdapterPosition(to) as? ViewHolder
                val targetBtn = if (isUpButton) holder?.upButton else holder?.downButton
                targetBtn?.requestFocus()
            }
        }
    }
}
