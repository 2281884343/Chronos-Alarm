package com.mobilealarm.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 一列可循环滚动的“滚轮”。中间固定高亮，停止滚动后把当前项回调出去。
 *
 * 用足够长的虚拟列表模拟无限循环：向上或向下都能一直滚，
 * 不会在 00 或 59 处突然停住。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 44.dp,
    visibleCount: Int = 5,
    loop: Boolean = true,
) {
    val count = items.size.coerceAtLeast(1)
    val cycleCount = if (loop && count > 1) count * 1000 else count
    val startIndex = if (loop && count > 1) count * 500 else 0
    val safeSelected = selectedIndex.coerceIn(0, count - 1)
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = startIndex + safeSelected,
    )
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val half = visibleCount / 2

    // 滚动停止后再提交，避免拖动过程中的中间值触发频繁重组。
    LaunchedEffect(state, selectedIndex, count, loop) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { (scrolling, index) ->
                if (!scrolling) {
                    val safe = if (loop && count > 1) {
                        ((index % count) + count) % count
                    } else {
                        index.coerceIn(0, count - 1)
                    }
                    if (safe != selectedIndex) onSelected(safe)
                }
            }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    RoundedCornerShape(14.dp),
                ),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = itemHeight * half),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = List(cycleCount) { index ->
                    if (loop && count > 1) index % count else index
                },
            ) { _, itemIndex ->
                val selected = itemIndex == safeSelected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = items[itemIndex],
                        fontSize = if (selected) 26.sp else 20.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun WheelTimePickerSheet(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    var amPmIndex by remember { mutableIntStateOf(if (initialHour < 12) 0 else 1) }
    var hourIndex by remember { mutableIntStateOf((initialHour + 11) % 12) }
    var minuteIndex by remember { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    val haptic = LocalHapticFeedback.current

    fun tick() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // 不用 ModalBottomSheet：它的下拉手势会和滚轮争夺垂直滑动，
    // 手指在分钟列向下滑时很容易把整页拖走。这里改成固定底部的 Dialog。
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                tonalElevation = 0.dp,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 18.dp, bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "设置时间",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        WheelPicker(
                            items = listOf("上午", "下午"),
                            selectedIndex = amPmIndex,
                            onSelected = {
                                if (it != amPmIndex) {
                                    amPmIndex = it
                                    tick()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        WheelPicker(
                            items = (1..12).map { it.toString() },
                            selectedIndex = hourIndex,
                            onSelected = {
                                if (it != hourIndex) {
                                    hourIndex = it
                                    tick()
                                }
                            },
                            modifier = Modifier.weight(0.9f),
                        )
                        WheelPicker(
                            items = (0..59).map { "%02d".format(it) },
                            selectedIndex = minuteIndex,
                            onSelected = {
                                if (it != minuteIndex) {
                                    minuteIndex = it
                                    tick()
                                }
                            },
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val hour24 = (hourIndex + 1) % 12 + if (amPmIndex == 1) 12 else 0
                            onConfirm(hour24, minuteIndex)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("确定", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
