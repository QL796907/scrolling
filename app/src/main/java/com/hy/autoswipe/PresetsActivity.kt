package com.hy.autoswipe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hy.autoswipe.databinding.ActivityPresetsBinding
import com.hy.autoswipe.databinding.ItemPresetManageBinding

class PresetsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPresetsBinding
    private lateinit var store: IntervalStore
    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPresetsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = IntervalStore(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAdd.setOnClickListener { savePreset() }
        binding.btnCancelEdit.setOnClickListener { clearForm() }
        renderList()
    }

    private fun savePreset() {
        val parsed = parseInputs() ?: return
        val (name, min, max) = parsed
        val editing = editingId
        val preset = if (editing == null) {
            store.addPreset(name, min, max).also { store.selectPreset(it.id) }
        } else {
            store.updatePreset(editing, name, min, max)
        }
        if (preset == null) {
            Toast.makeText(this, "没有找到要编辑的选项", Toast.LENGTH_SHORT).show()
            return
        }
        OverlayService.instance?.reloadInterval()
        Toast.makeText(this, "已保存 ${preset.label()}", Toast.LENGTH_SHORT).show()
        clearForm()
        renderList()
    }

    private fun startEdit(preset: IntervalPreset) {
        editingId = preset.id
        binding.formTitle.text = "编辑选项"
        binding.btnAdd.text = "保存修改"
        binding.btnCancelEdit.visibility = View.VISIBLE
        binding.inputName.setText(preset.name)
        binding.inputMin.setText(preset.min.toString())
        binding.inputMax.setText(preset.max.toString())
        binding.inputName.requestFocus()
        binding.inputName.setSelection(binding.inputName.text.length)
    }

    private fun clearForm() {
        editingId = null
        binding.formTitle.text = "新增选项"
        binding.btnAdd.text = "添加并保存"
        binding.btnCancelEdit.visibility = View.GONE
        binding.inputName.text.clear()
        binding.inputMin.text.clear()
        binding.inputMax.text.clear()
    }

    private fun parseInputs(): Triple<String, Int, Int>? {
        val min = binding.inputMin.text.toString().toIntOrNull()
        val max = binding.inputMax.text.toString().toIntOrNull()
        if (min == null || max == null) {
            Toast.makeText(this, "请填写最小秒和最大秒", Toast.LENGTH_SHORT).show()
            return null
        }
        if (min < 1 || max < 1) {
            Toast.makeText(this, "秒数至少为 1", Toast.LENGTH_SHORT).show()
            return null
        }
        if (max < min) {
            Toast.makeText(this, "最大秒不能小于最小秒", Toast.LENGTH_SHORT).show()
            return null
        }
        return Triple(binding.inputName.text.toString(), min, max)
    }

    private fun renderList() {
        binding.presetList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        store.presets().forEach { preset ->
            val item = ItemPresetManageBinding.inflate(inflater, binding.presetList, false)
            item.presetLabel.text = preset.label()
            item.btnEdit.setOnClickListener { startEdit(preset) }
            item.btnDelete.setOnClickListener {
                store.deletePreset(preset.id)
                OverlayService.instance?.reloadInterval()
                if (editingId == preset.id) clearForm()
                renderList()
            }
            binding.presetList.addView(item.root)
        }
    }
}
