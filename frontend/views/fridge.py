"""Fridge page module"""
import streamlit as st
from api import add_to_fridge, remove_from_fridge, update_item_count


def render():
    """Render the Fridge page"""
    st.markdown('<p class="section-header">🧊 My Fridge</p>', unsafe_allow_html=True)
    
    col1, col2 = st.columns([2, 3])
    
    with col1:
        st.markdown("### Add Ingredients")
        new_item = st.text_input("Ingredient name:", placeholder="e.g., bread, eggs, milk", key="add_item_input")
        item_count = st.number_input("Quantity:", min_value=1, max_value=100, value=1, key="add_item_count")
        
        if st.button("➕ Add to Fridge", type="primary", use_container_width=True):
            if new_item.strip():
                item_lower = new_item.strip().lower()
                result = add_to_fridge(item_lower, item_count)
                if result == "exists":
                    st.toast(f"'{new_item}' already in fridge! Added {item_count} more.")
                elif result == "added":
                    st.toast(f"Added '{new_item}' (x{item_count}) to fridge!")
                # Clear input fields by setting empty values
                st.session_state.add_item_input = ""
                st.session_state.add_item_count = 1
                st.rerun()
            else:
                st.warning("Please enter an ingredient name.")
    
    with col2:
        st.markdown("### Current Contents")
        if st.session_state.fridge_items:
            # Display items sorted by name
            for item in sorted(st.session_state.fridge_items.keys()):
                count = st.session_state.fridge_items.get(item, 1)
                col_item, col_count, col_btn = st.columns([3, 2, 1])
                with col_item:
                    st.write(f"**{item}**")
                with col_count:
                    new_count = st.number_input(
                        "Count", 
                        min_value=1, 
                        max_value=100, 
                        value=count, 
                        key=f"count_{item}",
                        label_visibility="collapsed"
                    )
                    if new_count != count:
                        update_item_count(item, new_count)
                with col_btn:
                    if st.button("🗑️", key=f"remove_{item}", help=f"Remove {item}"):
                        remove_from_fridge(item)
                        st.rerun()
        else:
            st.info("Your fridge is empty. Add some ingredients!")
