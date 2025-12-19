"""Recipe Book page module"""
import streamlit as st
from api import fetch_recipes_by_cuisine, fetch_cuisines, fetch_recipe_details, add_recipe, delete_recipe


def render():
    """Render the Recipe Book page"""
    # Fetch data
    recipes_by_cuisine = fetch_recipes_by_cuisine()
    cuisines = fetch_cuisines()
    
    # Sidebar for cuisine filter
    st.sidebar.divider()
    st.sidebar.markdown("### 🍽️ Cuisines")
    
    if st.sidebar.button("📋 All Recipes", use_container_width=True, type="primary" if st.session_state.selected_cuisine is None else "secondary"):
        st.session_state.selected_cuisine = None
        st.rerun()
    
    for cuisine in cuisines:
        cuisine_name = cuisine.get('name', '')
        display_name = cuisine.get('displayName', cuisine_name)
        recipe_count = len(recipes_by_cuisine.get(cuisine_name, []))
        
        if st.sidebar.button(f"{display_name} ({recipe_count})", use_container_width=True, 
                            type="primary" if st.session_state.selected_cuisine == cuisine_name else "secondary",
                            key=f"cuisine_{cuisine_name}"):
            st.session_state.selected_cuisine = cuisine_name
            st.rerun()
    
    # Main content with tabs
    tab1, tab2 = st.tabs(["📖 Browse Recipes", "➕ Add New Recipe"])
    
    with tab1:
        _render_browse_tab(recipes_by_cuisine, cuisines)
    
    with tab2:
        _render_add_tab(cuisines)


def _render_browse_tab(recipes_by_cuisine, cuisines):
    """Render the Browse Recipes tab"""
    st.markdown('<p class="section-header">📖 Recipe Book</p>', unsafe_allow_html=True)
    
    # Filter recipes based on selected cuisine
    if st.session_state.selected_cuisine:
        filtered_recipes = {st.session_state.selected_cuisine: recipes_by_cuisine.get(st.session_state.selected_cuisine, [])}
        st.info(f"Showing recipes for: **{st.session_state.selected_cuisine}**")
    else:
        filtered_recipes = recipes_by_cuisine
    
    if filtered_recipes:
        for cuisine, recipes in filtered_recipes.items():
            if recipes:
                st.markdown(f"### {cuisine}")
                for recipe in recipes:
                    recipe_name = recipe.get('name', 'Unknown')
                    ingredients = recipe.get('ingredients', [])
                    is_cookable = recipe_name in st.session_state.cookable_recipes
                    
                    with st.expander(f"{'✅ ' if is_cookable else ''}{recipe_name}", expanded=False):
                        st.markdown("**Ingredients:**")
                        ingredient_html = " ".join([
                            f'<span class="ingredient-chip">{ing}</span>' 
                            for ing in ingredients
                        ])
                        st.markdown(ingredient_html, unsafe_allow_html=True)
                        
                        col_details, col_delete = st.columns([3, 1])
                        with col_details:
                            if st.button(f"View Details", key=f"details_{cuisine}_{recipe_name}"):
                                details = fetch_recipe_details(recipe_name)
                                if details:
                                    st.markdown("---")
                                    st.markdown(f"**Instructions:** {details.get('instructions', 'No instructions available.')}")
                                    if details.get('imageUrl'):
                                        st.image(details['imageUrl'], caption=recipe_name)
                        with col_delete:
                            if st.button("🗑️ Delete", key=f"delete_{cuisine}_{recipe_name}", type="secondary"):
                                if delete_recipe(recipe_name):
                                    st.success(f"Deleted '{recipe_name}'!")
                                    st.rerun()
                                else:
                                    st.error("Failed to delete recipe.")
    else:
        st.info("No recipes available. Add some recipes using the 'Add New Recipe' tab!")


def _render_add_tab(cuisines):
    """Render the Add New Recipe tab"""
    st.markdown('<p class="section-header">➕ Add New Recipe</p>', unsafe_allow_html=True)
    
    with st.form("add_recipe_form"):
        recipe_name = st.text_input("Recipe Name:", placeholder="e.g., Grilled Cheese Sandwich")
        
        # Cuisine selection - fetched from backend
        cuisine_options = [c.get('name', '') for c in cuisines] if cuisines else []
        if not cuisine_options:
            st.warning("Could not fetch cuisine types from backend.")
        selected_cuisine = st.selectbox("Cuisine Type:", cuisine_options if cuisine_options else ['OTHER'])
        
        # Ingredients input
        ingredients_text = st.text_area(
            "Ingredients (one per line):", 
            placeholder="bread\ncheese\nbutter",
            height=150
        )
        
        # Instructions
        instructions = st.text_area(
            "Instructions:", 
            placeholder="Step-by-step cooking instructions...",
            height=150
        )
        
        submitted = st.form_submit_button("➕ Add Recipe", type="primary", use_container_width=True)
        
        if submitted:
            if not recipe_name.strip():
                st.error("Please enter a recipe name.")
            elif not ingredients_text.strip():
                st.error("Please enter at least one ingredient.")
            else:
                # Parse ingredients
                ingredients = [ing.strip().lower() for ing in ingredients_text.strip().split('\n') if ing.strip()]
                
                if add_recipe(recipe_name.strip().lower(), ingredients, selected_cuisine, instructions.strip()):
                    st.success(f"Recipe '{recipe_name}' added successfully!")
                    st.rerun()
                else:
                    st.error("Failed to add recipe. Please try again.")
