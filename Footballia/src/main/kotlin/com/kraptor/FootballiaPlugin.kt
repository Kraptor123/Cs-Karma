package com.kraptor

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import org.json.JSONArray
import kotlin.math.min

@CloudstreamPlugin
class FootballiaPlugin : Plugin() {

    override fun load(context: Context) {
        registerMainAPI(Footballia())
        this.openSettings = { ctx -> SettingsManager.showSettingsDialog(ctx as AppCompatActivity) }
    }

    companion object {
        private const val PREFS_PREFIX = "Footballia_"
        private const val COUNTRIES_ORDER_KEY = "${PREFS_PREFIX}COUNTRIES_order"
        private const val CLUBS_ORDER_KEY = "${PREFS_PREFIX}CLUBS_order"
        private const val PRIMARY_COLOR = "#8842f3"

        private val clubCategories = listOf(
            "Galatasaray" to "teams/galatasaray-sk",
            "Fenerbahçe" to "teams/fenerbahce-sk",
            "Beşiktaş" to "teams/besiktas-jk",
            "Real Madrid" to "teams/real-madrid",
            "Barcelona" to "teams/barcelona",
            "Atletico Madrid" to "teams/atletico-madrid",
            "Manchester United" to "teams/manchester-united",
            "Liverpool" to "teams/liverpool",
            "Manchester City" to "teams/manchester-city",
            "Chelsea" to "teams/chelsea",
            "Arsenal" to "teams/arsenal",
            "Bayern Munich" to "teams/bayern-munchen",
            "Borussia Dortmund" to "teams/borussia-dortmund",
            "Juventus" to "teams/juventus",
            "AC Milan" to "teams/ac-milan",
            "Inter Milan" to "teams/internazionale",
            "Paris Saint-Germain" to "teams/psg",
            "Olympique Marseille" to "teams/olympique-marseille",
            "Ajax" to "teams/ajax",
            "Porto" to "teams/fc-porto",
            "Benfica" to "teams/s-l-benfica"
        )

        private val countryAndLeagueCategories = listOf(
            "Süper Lig" to "competitions/super-lig",
            "Şampiyonlar Ligi" to "competitions/champions-league",
            "Dünya Kupası" to "competitions/world-cup",
            "Uefa Kupası" to "competitions/uefa-cup",
            "Avrupa Kupası" to "competitions/european-cup",
            "Kıtalar Arası Kupası" to "competitions/intercontinental-cup",
            "Euro" to "competitions/euro",
            "Copa América" to "competitions/copa-america",
            "Afrika Kupası" to "competitions/african-cup-of-nations",
            "Asya Kupası" to "competitions/asian-cup",
            "Olimpiyat Oyunları" to "competitions/olympic-games",
            "FIFA Kulüpler Dünya Kupası" to "competitions/club-world-cup",
            "Avrupa Ligi" to "competitions/europa-league",
            "Kupa Galipleri Kupası" to "competitions/cup-winners-cup",
            "UEFA Süper Kupa" to "competitions/super-cup",
            "Premier League" to "competitions/premier-league",
            "FA Cup" to "competitions/fa-cup",
            "La Liga" to "competitions/liga-1-division",
            "Copa del Rey" to "competitions/copa-del-rey",
            "Serie A" to "competitions/serie-a",
            "Coppa Italia" to "competitions/coppa-italia",
            "Bundesliga" to "competitions/bundesliga",
            "DFB-Pokal" to "competitions/dfb-pokal",
            "Ligue 1" to "competitions/ligue-1",
            "Coupe de France" to "competitions/coupe-de-france",
            "Copa Libertadores" to "competitions/copa-libertadores",
            "Brasileirão" to "competitions/campeonato-brasileiro",
            "CONCACAF Şampiyonlar Ligi" to "competitions/concacaf-champions-league",
            "MLS (ABD)" to "competitions/major-league-soccer",
            "CAF Şampiyonlar Ligi" to "competitions/caf-champions-league",
            "AFC Şampiyonlar Ligi" to "competitions/asian-champions-league",
            "Türkiye Kupası" to "competitions/turkish-cup"
        )

        val allCategories = clubCategories + countryAndLeagueCategories
        private val defaultEnabledNames = setOf(
            "Galatasaray", "Fenerbahçe", "Beşiktaş",
            "Süper Lig", "Şampiyonlar Ligi", "Dünya Kupası"
        )

        fun isCategoryEnabled(categoryName: String): Boolean {
            return getKey("${PREFS_PREFIX}${categoryName}_enabled")
                ?: defaultEnabledNames.contains(categoryName)
        }

        fun setCategoryEnabled(categoryName: String, enabled: Boolean) {
            setKey("${PREFS_PREFIX}${categoryName}_enabled", enabled)
        }

        fun getOrderedCategories(key: String, defaultList: List<String>): List<String> {
            val savedOrderJson: String? = getKey(key)
            return if (savedOrderJson != null) {
                try {
                    val jsonArray = JSONArray(savedOrderJson)
                    val savedList = (0 until jsonArray.length()).map { jsonArray.getString(it) }
                    val newItems = defaultList - savedList.toSet()
                    savedList + newItems
                } catch (e: Exception) {
                    defaultList
                }
            } else {
                defaultList
            }
        }

        fun setOrderedCategories(key: String, list: List<String>) {
            val jsonArray = JSONArray().apply { list.forEach { put(it) } }
            setKey(key, jsonArray.toString())
        }

        private fun restartApp(context: Context) {
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.component?.let { componentName ->
                val restartIntent = Intent.makeRestartActivityTask(componentName)
                context.startActivity(restartIntent)
                Runtime.getRuntime().exit(0)
            }
        }

        private object SettingsManager {
            private enum class Tab(val key: String, val title: String) {
                COUNTRIES("COUNTRIES", "Ülke ve Ligler"),
                CLUBS("CLUBS", "Kulüpler")
            }

            fun showSettingsDialog(activity: AppCompatActivity) {
                val controller = DialogController(activity)
                val dialogView = controller.rootView
                val dialog = createDialog(activity, dialogView)
                dialog.show()
                setupDialogButtons(dialog, activity, controller)
            }

            private fun createDialog(activity: AppCompatActivity, view: View): AlertDialog {
                return AlertDialog.Builder(activity)
                    .setTitle("Kategorileri Yönetin")
                    .setView(view)
                    .setPositiveButton("Tamam", null)
                    .setNegativeButton("İptal") { dialog, _ -> dialog.dismiss() }
                    .setCancelable(true)
                    .create()
            }

            private fun setupDialogButtons(dialog: AlertDialog, activity: AppCompatActivity, controller: DialogController) {
                val buttonColor = Color.parseColor(PRIMARY_COLOR)

                val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positive.id = android.R.id.button1
                positive.setTextColor(buttonColor)

                val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                negative.setTextColor(Color.GRAY)

                try {
                    val rv = controller.recyclerView
                    positive.nextFocusUpId = rv.id
                    negative.nextFocusUpId = rv.id
                } catch (_: Exception) {

                }

                fun safeMoveFocusToList(buttonView: View): Boolean {
                    val rv = try { controller.recyclerView } catch (_: Exception) { null } ?: return false
                    val adapter = rv.adapter as? CategoryAdapter ?: return false
                    val count = adapter.itemCount
                    if (count == 0) return false

                    val selectedCount = adapter.getSelectedCount()
                    val targetPos = if (selectedCount > 0) selectedCount - 1 else 0

                    rv.scrollToPosition(targetPos)
                    rv.post {
                        val vh = rv.findViewHolderForAdapterPosition(targetPos)
                        if (vh is CategoryAdapter.ViewHolder) {
                            vh.checkBox.requestFocus()
                        }
                    }
                    return true
                }

                positive.setOnKeyListener { v, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_UP) {
                        safeMoveFocusToList(v)
                        true
                    } else false
                }

                negative.setOnKeyListener { v, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_UP) {
                        safeMoveFocusToList(v)
                        true
                    } else false
                }

                dialog.setOnKeyListener { dialogInterface, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        dialogInterface.dismiss()
                        true
                    } else {
                        false
                    }
                }

                positive.setOnClickListener {
                    AlertDialog.Builder(activity)
                        .setTitle("Emin misiniz?")
                        .setMessage("Değişikliklerin tamamlanması için yeniden başlatmalısınız!")
                        .setPositiveButton("Evet, Yeniden Başlat") { _, _ -> restartApp(activity) }
                        .setNegativeButton("İptal", null)
                        .show()
                        .apply {
                            getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
                            getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.GRAY)
                        }
                }
            }

            private class DialogController(private val activity: AppCompatActivity) {
                private var currentTab = Tab.COUNTRIES
                private val buttonColor = Color.parseColor(PRIMARY_COLOR)

                private lateinit var countriesTabButton: Button
                private lateinit var clubsTabButton: Button

                lateinit var recyclerView: RecyclerView
                lateinit var adapter: CategoryAdapter

                private var recyclerId: Int = View.generateViewId()

                val rootView: View = createRootLayout()

                private fun createRootLayout(): View {
                    return LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(30, 20, 30, 20)

                        addView(createWarningText())
                        addView(createTabLayout())
                        addView(createRecyclerView())
                    }
                }

                private fun createWarningText(): TextView {
                    return TextView(activity).apply {
                        text = "Not: Çok fazla kategori seçmek eklentiyi bozabilir!"
                        setTextColor(Color.RED)
                        setPadding(0, 0, 0, 24)
                    }
                }

                private fun createTabLayout(): LinearLayout {
                    return LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        setPadding(0, 0, 0, 20)

                        countriesTabButton = createTabButton(Tab.COUNTRIES, true)
                        clubsTabButton = createTabButton(Tab.CLUBS, false)

                        val tabParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1.0f
                        )

                        addView(countriesTabButton, tabParams)
                        addView(clubsTabButton, tabParams)
                    }
                }

                private fun createTabButton(tab: Tab, isActive: Boolean): Button {
                    return Button(activity).apply {
                        text = tab.title
                        updateTabButtonStyle(isActive)
                        setOnClickListener { switchTab(tab) }

                        val padding = dpToPx(12)
                        setPadding(padding, dpToPx(10), padding, dpToPx(10))

                        isFocusable = true
                        isFocusableInTouchMode = true

                        background = null
                        stateListAnimator = null

                        setTextColor(createTabTextColorStateList(isActive))
                    }
                }

                private fun createTabTextColorStateList(isActive: Boolean): ColorStateList {
                    return ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_activated),
                            intArrayOf(android.R.attr.state_focused),
                            intArrayOf(android.R.attr.state_pressed),
                            intArrayOf()
                        ),
                        intArrayOf(
                            if (isActive) Color.WHITE else buttonColor,
                            Color.parseColor("#00D9FF"),
                            Color.parseColor("#00D9FF"),
                            if (isActive) Color.WHITE else buttonColor
                        )
                    )
                }

                private fun Button.updateTabButtonStyle(isActive: Boolean) {
                    background = null
                    setTextColor(createTabTextColorStateList(isActive))
                    isActivated = isActive
                }

                private fun createRecyclerView(): RecyclerView {
                    adapter = CategoryAdapter(
                        onCheckedChange = { name, enabled ->
                            setCategoryEnabled(name, enabled)
                        }
                    )

                    val rv = RecyclerView(activity).apply {
                        id = recyclerId
                        layoutManager = LinearLayoutManager(activity)
                        this@DialogController.recyclerView = this
                        this.adapter = this@DialogController.adapter
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }

                    adapter.setRecyclerView(rv)
                    refreshCategoryList()
                    return rv
                }

                private fun switchTab(tab: Tab) {
                    if (currentTab == tab) return
                    currentTab = tab

                    countriesTabButton.updateTabButtonStyle(tab == Tab.COUNTRIES)
                    clubsTabButton.updateTabButtonStyle(tab == Tab.CLUBS)

                    countriesTabButton.refreshDrawableState()
                    clubsTabButton.refreshDrawableState()

                    refreshCategoryList()
                }

                private fun refreshCategoryList() {
                    val defaultList = if (currentTab == Tab.COUNTRIES) {
                        countryAndLeagueCategories.map { it.first }
                    } else {
                        clubCategories.map { it.first }
                    }

                    val orderKey = if (currentTab == Tab.COUNTRIES) {
                        COUNTRIES_ORDER_KEY
                    } else {
                        CLUBS_ORDER_KEY
                    }

                    val orderedList = getOrderedCategories(orderKey, defaultList)
                    adapter.setOrderKey(orderKey)
                    adapter.setList(orderedList)
                }

                private fun dpToPx(dp: Int): Int {
                    return TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        dp.toFloat(),
                        activity.resources.displayMetrics
                    ).toInt()
                }
            }
        }

        private class CategoryAdapter(
            private val onCheckedChange: (String, Boolean) -> Unit
        ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

            private val items = mutableListOf<String>()
            private var orderKey: String = ""
            private var recyclerViewRef: RecyclerView? = null

            fun setRecyclerView(rv: RecyclerView) { recyclerViewRef = rv }
            fun setOrderKey(key: String) { orderKey = key }

            fun getSelectedCount(): Int = items.count { isCategoryEnabled(it) }

            fun setList(newList: List<String>) {
                val selected = newList.filter { isCategoryEnabled(it) }.toMutableList()
                val unselected = newList.filter { !isCategoryEnabled(it) }.sortedBy { it.lowercase() }.toMutableList()
                items.clear()
                items.addAll(selected)
                items.addAll(unselected)
                notifyDataSetChanged()

                recyclerViewRef?.post {
                    val rv = recyclerViewRef ?: return@post
                    if (items.isNotEmpty()) {
                        val vh = rv.findViewHolderForAdapterPosition(0)
                        (vh as? ViewHolder)?.checkBox?.requestFocus()
                    }
                }
            }

            class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
                val container: LinearLayout = view as LinearLayout
                val checkBox: CheckBox = view.findViewById(R.id.checkbox)
                val upButton: Button = view.findViewById(R.id.btn_up)
                val downButton: Button = view.findViewById(R.id.btn_down)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = createItemLayout(parent.context)
                return ViewHolder(view)
            }

            override fun getItemCount() = items.size

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val name = items[position]

                holder.checkBox.text = name
                holder.checkBox.isChecked = isCategoryEnabled(name)
                holder.container.setBackgroundColor(Color.TRANSPARENT)
                holder.container.isPressed = false
                holder.itemView.isPressed = false
                holder.checkBox.isPressed = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try { holder.container.foreground = null } catch (_: Exception) {}
                }

                val selectedCount = getSelectedCount()
                val isInSelected = position < selectedCount

                holder.container.isClickable = true
                holder.container.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    holder.checkBox.requestFocus()
                    val newState = !isCategoryEnabled(items[pos])
                    holder.checkBox.isChecked = newState
                    handleToggle(items[pos], newState, pos)
                }

                holder.checkBox.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_DOWN) holder.checkBox.requestFocus()
                    false
                }

                holder.checkBox.setOnClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                    val newState = holder.checkBox.isChecked
                    handleToggle(items[pos], newState, pos)
                }

                holder.checkBox.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_UP &&
                        (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
                    ) {
                        val pos = holder.bindingAdapterPosition
                        if (pos == RecyclerView.NO_POSITION) return@setOnKeyListener true
                        val newState = !isCategoryEnabled(items[pos])
                        holder.checkBox.isChecked = newState
                        handleToggle(items[pos], newState, pos)
                        true
                    } else false
                }

                val canMoveUp: Boolean
                val canMoveDown: Boolean

                if (!isInSelected) {
                    canMoveUp = false
                    canMoveDown = false
                } else {
                    if (selectedCount == 1) {
                        canMoveUp = true
                        canMoveDown = true
                    } else {
                        canMoveUp = position > 0
                        canMoveDown = position < (selectedCount - 1)
                    }
                }

                holder.upButton.apply {
                    isEnabled = canMoveUp
                    alpha = if (canMoveUp) 1f else 0.3f
                    isFocusable = canMoveUp
                    isFocusableInTouchMode = canMoveUp

                    setOnClickListener {
                        val cur = holder.bindingAdapterPosition
                        if (cur != RecyclerView.NO_POSITION && cur > 0) moveItem(cur, cur - 1, R.id.btn_up)
                    }

                    setOnLongClickListener {
                        val cur = holder.bindingAdapterPosition
                        if (cur != RecyclerView.NO_POSITION && cur > 0) moveItemToTop(cur, R.id.btn_up)
                        true
                    }
                }

                holder.downButton.apply {
                    isEnabled = canMoveDown
                    alpha = if (canMoveDown) 1f else 0.3f
                    isFocusable = canMoveDown
                    isFocusableInTouchMode = canMoveDown

                    setOnClickListener {
                        val cur = holder.bindingAdapterPosition
                        if (cur != RecyclerView.NO_POSITION) {
                            val target = cur + 1
                            val lastIndexInSelected = getSelectedCount() - 1
                            if (cur < lastIndexInSelected) moveItem(cur, target.coerceAtMost(lastIndexInSelected), R.id.btn_down)
                        }
                    }

                    setOnLongClickListener {
                        val cur = holder.bindingAdapterPosition
                        if (cur != RecyclerView.NO_POSITION) moveItemToBottom(cur, R.id.btn_down)
                        true
                    }
                }
            }

            private fun handleToggle(categoryName: String, newState: Boolean, fromPos: Int) {
                if (fromPos == RecyclerView.NO_POSITION) return

                val selectedBefore = items.count { it != categoryName && isCategoryEnabled(it) }

                if (newState) {
                    val toBefore = selectedBefore

                    setCategoryEnabled(categoryName, true)
                    onCheckedChange(categoryName, true)

                    val currentIndex = items.indexOf(categoryName)
                    if (currentIndex == -1) return

                    var adjustedTo = toBefore
                    if (currentIndex < toBefore) adjustedTo = toBefore - 1
                    adjustedTo = adjustedTo.coerceIn(0, items.size - 1)

                    if (currentIndex != adjustedTo) {
                        val it = items.removeAt(currentIndex)
                        items.add(adjustedTo, it)
                        notifyItemMoved(currentIndex, adjustedTo)
                        val start = min(currentIndex, adjustedTo)
                        val end = kotlin.math.max(currentIndex, adjustedTo)
                        notifyItemRangeChanged(start, end - start + 1)
                    } else {
                        setOrderedCategories(orderKey, items)
                    }

                    notifyItemRangeChanged(0, getSelectedCount())

                    val focusTarget = fromPos.coerceIn(0, items.size - 1)
                    ensureFocusOnPositionAndClearPressed(focusTarget)
                    setOrderedCategories(orderKey, items)
                    return
                } else {
                    val selectedCountNow = items.count { isCategoryEnabled(it) }
                    val unselected = items.filter { it != categoryName && !isCategoryEnabled(it) }.sortedBy { it.lowercase() }
                    val insertInUnselected = (unselected.indexOfFirst { it.lowercase() > categoryName.lowercase() }).let {
                        if (it == -1) unselected.size else it
                    }
                    var toBefore = selectedCountNow + insertInUnselected

                    setCategoryEnabled(categoryName, false)
                    onCheckedChange(categoryName, false)

                    val currentIndex = items.indexOf(categoryName)
                    if (currentIndex == -1) return

                    var adjustedTo = toBefore
                    if (currentIndex < toBefore) adjustedTo = toBefore - 1
                    adjustedTo = adjustedTo.coerceIn(0, items.size - 1)

                    if (currentIndex != adjustedTo) {
                        val it = items.removeAt(currentIndex)
                        items.add(adjustedTo, it)
                        notifyItemMoved(currentIndex, adjustedTo)
                        val start = min(currentIndex, adjustedTo)
                        val end = kotlin.math.max(currentIndex, adjustedTo)
                        notifyItemRangeChanged(start, end - start + 1)
                    } else {
                        setOrderedCategories(orderKey, items)
                    }

                    notifyItemRangeChanged(0, getSelectedCount())

                    val focusTarget = fromPos.coerceIn(0, items.size - 1)
                    ensureFocusOnPositionAndClearPressed(focusTarget)
                    setOrderedCategories(orderKey, items)
                    return
                }
            }

            private fun moveItem(from: Int, to: Int, focusTargetId: Int = R.id.checkbox) {
                if (from == RecyclerView.NO_POSITION) return
                if (from == to) return

                val item = items.removeAt(from)

                val boundedTo = to.coerceIn(0, items.size)

                items.add(boundedTo, item)

                notifyItemMoved(from, boundedTo)
                val start = min(from, boundedTo)
                val end = kotlin.math.max(from, boundedTo)
                notifyItemRangeChanged(start, end - start + 1)

                setOrderedCategories(orderKey, items)
                ensureFocusOnPositionAndClearPressed(boundedTo.coerceIn(0, items.size - 1), focusTargetId)
            }

            private fun moveItemToTop(from: Int, focusTargetId: Int = R.id.checkbox) {
                if (from == RecyclerView.NO_POSITION) return
                val selectedCount = getSelectedCount()
                if (from >= selectedCount) return
                if (from <= 0) return

                val item = items.removeAt(from)
                items.add(0, item)
                notifyItemMoved(from, 0)
                notifyItemRangeChanged(0, from + 1)
                setOrderedCategories(orderKey, items)
                ensureFocusOnPositionAndClearPressed(0, focusTargetId)
            }

            private fun moveItemToBottom(from: Int, focusTargetId: Int = R.id.checkbox) {
                if (from == RecyclerView.NO_POSITION) return
                val selectedCountBefore = getSelectedCount()
                if (from >= selectedCountBefore) return

                val targetAfterRemoval = selectedCountBefore - 1
                if (from == targetAfterRemoval) return

                val item = items.removeAt(from)
                val boundedTo = targetAfterRemoval.coerceIn(0, items.size)
                items.add(boundedTo, item)

                notifyItemMoved(from, boundedTo)
                val start = min(from, boundedTo)
                val end = kotlin.math.max(from, boundedTo)
                notifyItemRangeChanged(start, end - start + 1)

                setOrderedCategories(orderKey, items)
                ensureFocusOnPositionAndClearPressed(boundedTo.coerceIn(0, items.size - 1), focusTargetId)
            }

            private fun ensureFocusOnPositionAndClearPressed(position: Int, focusTargetId: Int = R.id.checkbox) {
                val rv = recyclerViewRef ?: return
                rv.scrollToPosition(position)
                rv.post {
                    val vh = rv.findViewHolderForAdapterPosition(position)
                    if (vh is ViewHolder) {
                        vh.itemView.findViewById<View>(focusTargetId)?.requestFocus()
                        vh.container.isPressed = false
                        vh.checkBox.isPressed = false
                        vh.upButton.isPressed = false
                        vh.downButton.isPressed = false
                        vh.itemView.isPressed = false
                    } else {
                        rv.postDelayed({ ensureFocusOnPositionAndClearPressed(position, focusTargetId) }, 40)
                    }
                }
            }

            private fun createItemLayout(context: Context): LinearLayout {
                return LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    val padding = dpToPx(context, 12)
                    setPadding(padding, padding, padding, padding)

                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setBackgroundColor(Color.TRANSPARENT)
                    isClickable = false
                    isLongClickable = false

                    addView(createCheckBox(context))
                    addView(createButtonContainer(context))
                }
            }

            private fun createCheckBox(context: Context): CheckBox {
                val checkedColor = Color.parseColor(PRIMARY_COLOR)
                val focusColor = Color.parseColor("#00D9FF")

                return CheckBox(context).apply {
                    id = R.id.checkbox
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f
                    )
                    buttonTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_focused, android.R.attr.state_checked),
                            intArrayOf(android.R.attr.state_focused, -android.R.attr.state_checked),
                            intArrayOf(-android.R.attr.state_checked),
                            intArrayOf(android.R.attr.state_checked)
                        ),
                        intArrayOf(focusColor, focusColor, Color.GRAY, checkedColor)
                    )
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnFocusChangeListener { _, hasFocus -> setTextColor(if (hasFocus) focusColor else Color.WHITE) }
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            }

            private fun createButtonContainer(context: Context): LinearLayout {
                return LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isFocusable = false
                    isFocusableInTouchMode = false

                    val buttonSize = dpToPx(context, 40)
                    val buttonMargin = dpToPx(context, 4)

                    addView(createNavButton(context, "▼", R.id.btn_down).apply {
                        layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply { marginEnd = buttonMargin }
                    })
                    addView(createNavButton(context, "▲", R.id.btn_up).apply {
                        layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
                    })
                }
            }

            private fun createNavButton(context: Context, symbol: String, id: Int): Button {
                val focusColor = Color.parseColor("#00D9FF")
                return Button(context).apply {
                    this.id = id
                    text = symbol
                    textSize = 14f
                    setBackgroundColor(Color.TRANSPARENT)
                    setTextColor(Color.WHITE)

                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus && isEnabled) {
                            setBackgroundColor(focusColor)
                            setTextColor(Color.BLACK)
                        } else {
                            setBackgroundColor(Color.TRANSPARENT)
                            setTextColor(if (isEnabled) Color.WHITE else Color.GRAY)
                        }
                    }

                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_UP &&
                            (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
                        ) {
                            if (isEnabled) callOnClick()
                            true
                        } else false
                    }

                    isFocusable = true
                    isFocusableInTouchMode = true

                    if (id == R.id.btn_up) nextFocusRightId = android.R.id.button1
                }
            }

            private fun dpToPx(context: Context, dp: Int): Int {
                return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dp.toFloat(),
                    context.resources.displayMetrics
                ).toInt()
            }

            private object R {
                object id {
                    const val checkbox = 1001
                    const val btn_up = 1002
                    const val btn_down = 1003
                }
            }
        }
    }
}