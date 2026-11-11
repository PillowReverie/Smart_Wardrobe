package com.example.wardrobe.ui.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.wardrobe.model.AiOutfitPlan;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 验证首页单一状态的不可变性与关键派生状态。
 */
public class MainUiStateTest {

    @Test
    public void builderCreatesTravelDerivedState() {
        MainUiState state = new MainUiState.Builder()
                .mode("TRAVEL")
                .scene("")
                .genderSelected(true)
                .weatherLoading(true)
                .aiLoading(false)
                .wardrobeCount(5)
                .outfitCount(2)
                .build();

        assertTrue(state.isTravelMode());
        assertTrue(state.isTravelInputEmpty());
        assertTrue(state.hasGender());
        assertTrue(state.isWeatherLoading());
        assertEquals(5, state.getWardrobeCount());
        assertEquals(2, state.getOutfitCount());
    }

    @Test
    public void planListCannotMutatePublishedState() {
        List<AiOutfitPlan> plans = new ArrayList<>();
        plans.add(new AiOutfitPlan());
        MainUiState state = new MainUiState.Builder()
                .aiPlans(plans)
                .contentType(MainUiState.ContentType.PLANS)
                .build();

        plans.clear();

        assertEquals(1, state.getAiPlans().size());
        assertFalse(state.isTravelMode());
    }
}
