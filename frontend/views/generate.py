"""Generate Recipes page module"""
import streamlit as st
from api import generate_cookable_recipes, fetch_recipe_details


def render():
    """Render the Generate Recipes page"""
    st.markdown('<p class="section-header">🍳 What Can I Cook?</p>', unsafe_allow_html=True)
    
    col1, col2 = st.columns([1, 2])
    
    with col1:
        st.markdown("### Your Fridge")
        if st.session_state.fridge_items:
            st.markdown("**Available ingredients:**")
            for item, count in sorted(st.session_state.fridge_items.items()):
                st.write(f"✓ {item} (x{count})")
        else:
            st.warning("Your fridge is empty! Go to the Fridge page to add ingredients.")
        
        st.divider()
        
        if st.button("🔍 Find Cookable Recipes", type="primary", use_container_width=True, disabled=not st.session_state.fridge_items):
            if generate_cookable_recipes():
                if st.session_state.cookable_recipes:
                    st.success(f"Found {len(st.session_state.cookable_recipes)} recipes you can make!")
                else:
                    st.info("No recipes found with current ingredients. Try adding more!")
            st.rerun()
    
    with col2:
        st.markdown("### Cookable Recipes")
        if st.session_state.cookable_recipes:
            st.markdown(f"**You can make {len(st.session_state.cookable_recipes)} recipe(s):**")
            
            for recipe in st.session_state.cookable_recipes:
                with st.container():
                    st.markdown(f'<div class="cookable-recipe">{recipe}</div>', unsafe_allow_html=True)
                    
                    details = fetch_recipe_details(recipe)
                    if details:
                        with st.expander("View recipe details"):
                            ingredients = details.get('ingredients', [])
                            ingredient_html = " ".join([
                                f'<span class="ingredient-chip">{ing}</span>' 
                                for ing in ingredients
                            ])
                            st.markdown("**Ingredients:**")
                            st.markdown(ingredient_html, unsafe_allow_html=True)
                            
                            st.markdown("**Instructions:**")
                            st.write(details.get('instructions', 'No instructions available.'))
                            
                            if details.get('imageUrl'):
                                st.image(details['imageUrl'], caption=recipe)
        else:
            st.info("Click 'Find Cookable Recipes' to see what you can make with your current ingredients!")
