package com.example.videoshield

import android.content.Context
/**
 * Cached pixel values for the shared UI dimension system.
 *
 * Programmatic views use this instead of scattering raw dp conversions through controllers.
 * A controller creates one instance and reuses it for its lifetime, avoiding repeated resource
 * lookups while keeping layout geometry sourced from the same tokens as XML.
 */
class UiMetrics(context: Context) {
    private val resources = context.resources

    fun px(id: Int): Int = resources.getDimensionPixelSize(id)

    val spaceXxs = px(R.dimen.ui_space_xxs)
    val spaceXs = px(R.dimen.ui_space_xs)
    val spaceSm = px(R.dimen.ui_space_sm)
    val spaceMd = px(R.dimen.ui_space_md)
    val spaceLg = px(R.dimen.ui_space_lg)
    val spaceXl = px(R.dimen.ui_space_xl)
    val space2Xl = px(R.dimen.ui_space_2xl)
    val space3Xl = px(R.dimen.ui_space_3xl)

    val touchTarget = px(R.dimen.ui_touch_target)
    val cardRadius = px(R.dimen.ui_radius_card)
    val pillRadius = px(R.dimen.ui_radius_pill)
    val largePillRadius = px(R.dimen.ui_radius_large_pill)
    val sheetCornerRadius = px(R.dimen.ui_sheet_corner_radius)
    val sheetHandleWidth = px(R.dimen.ui_sheet_handle_width)
    val sheetHandleHeight = px(R.dimen.ui_sheet_handle_height)
    val sheetRowMinHeight = px(R.dimen.ui_sheet_row_min_height)
    val sheetIconSize = px(R.dimen.ui_sheet_icon_size)
    val sheetCloseSize = px(R.dimen.ui_sheet_close_size)
    val sheetHorizontalPadding = px(R.dimen.ui_sheet_horizontal_padding)

    val libraryPagePadding = px(R.dimen.ui_library_page_padding)
    val libraryVideoCardWidth = px(R.dimen.ui_library_video_card_width)
    val libraryVideoCardHeight = px(R.dimen.ui_library_video_card_height)
    val libraryCollectionWidth = px(R.dimen.ui_library_collection_width)
    val libraryCollectionHeight = px(R.dimen.ui_library_collection_height)
    val libraryCardGap = px(R.dimen.ui_library_card_gap)
    val libraryShortcutMinHeight = px(R.dimen.ui_library_shortcut_min_height)
    val libraryProgressHeight = px(R.dimen.ui_library_progress_height)
    val librarySectionHeaderHeight = px(R.dimen.ui_library_section_header_height)

    val miniPlayerWidth = px(R.dimen.ui_mini_player_width)
    val miniPlayerMinWidth = px(R.dimen.ui_mini_player_min_width)
    val miniPlayerMargin = px(R.dimen.ui_mini_player_margin)
    val miniPlayerElevation = px(R.dimen.ui_mini_player_elevation)
    val miniPlayerDragTravelMin = px(R.dimen.ui_mini_player_drag_travel_min)
    val miniPlayerDragX = px(R.dimen.ui_mini_player_drag_x)
}
