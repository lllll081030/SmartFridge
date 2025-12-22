"""Generate Recipes page module - with Hybrid Search integration"""
import streamlit as st
from api import (
    generate_cookable_recipes, 
    fetch_recipe_details,
    search_recipes_semantic,
    hybrid_search_recipes,
    index_all_recipes,
    seed_ingredient_aliases,
    get_search_stats,
    get_almost_cookable_recipes,
    get_substitution_suggestions
)


def render():
    """Render the Generate Recipes page with hybrid search"""
    st.markdown('<p class="section-header">🍳 What Can I Cook?</p>', unsafe_allow_html=True)
    
    # Sync button at the top (does all admin work)
    col_sync, col_status = st.columns([1, 3])
    with col_sync:
        if st.button("🔄 Sync Search Index", help="Index all recipes for semantic search"):
            with st.spinner("Syncing..."):
                seed_ingredient_aliases()
                result = index_all_recipes()
                if result.get('error'):
                    st.error(result['error'])
                else:
                    st.success(f"✅ Indexed {result.get('count', 0)} recipes!")
    with col_status:
        stats = get_search_stats()
        if stats.get('initialized'):
            st.caption(f"📊 {stats.get('pointsCount', 0)} recipes indexed | Search: ✅ Ready")
        else:
            st.caption("⚠️ Search not ready - click Sync or start Qdrant/Ollama")
    
    st.divider()
    
    # Tabs for different search modes
    tab1, tab2, tab3 = st.tabs(["🥗 Exact Match", "🔍 Semantic Search", "🔗 Hybrid Search"])
    
    with tab1:
        _render_exact_match_tab()
    
    with tab2:
        _render_semantic_search_tab()
    
    with tab3:
        _render_hybrid_search_tab()


def _render_exact_match_tab():
    """Render exact ingredient matching (original functionality)"""
    col1, col2 = st.columns([1, 2])
    
    with col1:
        # Button at top
        if st.button("🔍 Find Cookable Recipes", type="primary", use_container_width=True, 
                    disabled=not st.session_state.fridge_items, key="exact_search_btn"):
            if generate_cookable_recipes():
                if st.session_state.cookable_recipes:
                    st.success(f"Found {len(st.session_state.cookable_recipes)} recipes!")
                else:
                    st.info("No recipes found. Try adding more ingredients!")
            st.rerun()
        
        st.divider()
        
        st.markdown("### Your Fridge")
        if st.session_state.fridge_items:
            st.markdown("**Available ingredients:**")
            for item, count in sorted(st.session_state.fridge_items.items()):
                st.write(f"✓ {item} (x{count})")
        else:
            st.warning("Your fridge is empty! Go to the Fridge page to add ingredients.")
    
    with col2:
        st.markdown("### ✅ Cookable Now")
        if st.session_state.cookable_recipes:
            st.markdown(f"**You can make {len(st.session_state.cookable_recipes)} recipe(s):**")
            _display_recipe_list(st.session_state.cookable_recipes)
        else:
            st.info("Click 'Find Cookable Recipes' to see what you can make!")
        
        st.divider()
        
        # Almost cookable recipes section - NOW WITH BUTTON
        st.markdown("### 🟡 Almost Cookable")
        st.caption("Recipes you're just 1-2 ingredients away from cooking!")
        
        # Button to find almost-cookable recipes
        if st.button("🔍 Find Almost-Cookable Recipes", key="almost_cookable_btn", use_container_width=True):
            with st.spinner("Finding recipes close to cookable..."):
                almost_cookable_data = get_almost_cookable_recipes(max_missing=2)
                st.session_state.almost_cookable_recipes = almost_cookable_data.get('recipes', {})
            st.rerun()
        
        # Display results if available
        if 'almost_cookable_recipes' in st.session_state and st.session_state.almost_cookable_recipes:
            almost_cookable = st.session_state.almost_cookable_recipes
            st.markdown(f"**{len(almost_cookable)} recipe(s) close to cookable:**")
            _display_almost_cookable_recipes(almost_cookable)
        elif 'almost_cookable_recipes' in st.session_state:
            # Button was clicked but no results
            st.info("💡 No almost-cookable recipes found. All recipes either need too many ingredients or are already cookable!")


def _display_almost_cookable_recipes(almost_cookable):
    """Display almost cookable recipes with substitution suggestions"""
    for recipe_name, missing_ingredients in almost_cookable.items():
        with st.container():
            # Recipe name with missing count badge
            missing_count = len(missing_ingredients)
            st.markdown(
                f'<div style="background: #FFF9C4; padding: 12px; border-radius: 8px; margin-bottom: 8px;">'
                f'<strong style="font-size: 1.1em;">{recipe_name}</strong> '
                f'<span style="background: #FF9800; color: white; padding: 2px 8px; border-radius: 12px; font-size: 0.85em;">'
                f'Missing: {missing_count}</span>'
                f'</div>',
                unsafe_allow_html=True
            )
            
            # Expandable section for missing ingredients and substitutions
            with st.expander(f"🔍 View missing ingredients & find substitutes"):
                st.markdown("**Missing ingredients:**")
                for ing in missing_ingredients:
                    st.markdown(f"- ❌ {ing}")
                
                # Button to get AI substitution suggestions
                if st.button(f"✨ Get AI Substitutes", key=f"sub_btn_{recipe_name}"):
                    with st.spinner("Asking AI for suggestions..."):
                        sub_data = get_substitution_suggestions(recipe_name)
                        
                        if sub_data and sub_data.get('substitutions'):
                            substitutions = sub_data['substitutions']
                            
                            st.success("✅ AI found substitution suggestions!")
                            
                            for missing_ing, suggestions in substitutions.items():
                                if suggestions:
                                    st.markdown(f"**Substitutes for '{missing_ing}':**")
                                    
                                    for idx, suggestion in enumerate(suggestions, 1):
                                        # DEBUG: Print what we're receiving
                                        print(f"[FRONTEND DEBUG] Suggestion {idx}: {suggestion}")
                                        
                                        substitute = suggestion.get('ingredient', 'Unknown')
                                        confidence = suggestion.get('confidence', 0.0)
                                        in_fridge = suggestion.get('inFridge', False)
                                        reasoning = suggestion.get('reasoning', '')
                                        
                                        # Confidence bar color
                                        if confidence >= 0.8:
                                            conf_color = "#4CAF50"  # Green
                                        elif confidence >= 0.6:
                                            conf_color = "#FF9800"  # Orange
                                        else:
                                            conf_color = "#F44336"  # Red
                                        
                                        # In fridge badge
                                        fridge_badge = "✓ In your fridge!" if in_fridge else ""
                                        fridge_style = "background: #4CAF50; color: white; padding: 2px 8px; border-radius: 4px; font-size: 0.8em; margin-left: 8px;" if in_fridge else ""
                                        
                                        st.markdown(
                                            f'<div style="background: #F5F5F5; padding: 10px; border-radius: 6px; margin-bottom: 8px;">'
                                            f'<strong>{idx}. {substitute}</strong> '
                                            f'<span style="{fridge_style}">{fridge_badge}</span><br>'
                                            f'<div style="background: {conf_color}; height: 4px; width: {confidence*100}%; border-radius: 2px; margin: 4px 0;"></div>'
                                            f'<small style="color: #666;">Confidence: {confidence:.0%}</small><br>'
                                            f'<small style="color: #444;">{reasoning}</small>'
                                            f'</div>',
                                            unsafe_allow_html=True
                                        )
                                else:
                                    st.info(f"No substitutes found for '{missing_ing}'")
                        else:
                            st.warning("⚠️ Could not get AI suggestions. Make sure the AI service is running on port 5001.")


def _render_semantic_search_tab():
    """Render semantic search tab"""
    st.markdown("### Search by meaning, not just keywords")
    st.info("💡 Try: 'quick Italian dinner', 'healthy breakfast', 'spicy Asian noodles'")
    
    col1, col2 = st.columns([4, 1])
    with col1:
        query = st.text_input("Describe what you want to cook:", key="semantic_query", 
                             placeholder="e.g., 'something quick with chicken'")
    with col2:
        limit = st.number_input("Max results", min_value=1, max_value=20, value=5, key="semantic_limit")
    
    if st.button("🔍 Search", key="semantic_search_btn", type="primary"):
        if query:
            with st.spinner("Searching..."):
                results, warning = search_recipes_semantic(query, limit)
                
                if warning:
                    st.warning(warning)
                
                if results:
                    st.success(f"Found {len(results)} matching recipes")
                    _display_search_results(results)
                else:
                    st.info("No matching recipes found. Try a different query or sync the index.")
        else:
            st.warning("Please enter a search query")


def _render_hybrid_search_tab():
    """Render hybrid search tab (ingredients + semantic)"""
    st.markdown("### Combine your ingredients with what you're craving")
    
    col1, col2 = st.columns(2)
    
    with col1:
        # Auto-populate with fridge items
        fridge_list = list(st.session_state.fridge_items.keys()) if st.session_state.fridge_items else []
        default_ingredients = '\n'.join(fridge_list)
        
        ingredients_input = st.text_area(
            "Your ingredients (one per line):",
            value=default_ingredients,
            height=120,
            key="hybrid_ingredients",
            help="These are auto-filled from your fridge"
        )
    
    with col2:
        query = st.text_input(
            "What are you in the mood for? (optional)",
            key="hybrid_query",
            placeholder="e.g., 'something quick and healthy'"
        )
        
        limit = st.number_input("Max results", min_value=1, max_value=20, value=5, key="hybrid_limit")
    
    if st.button("🔗 Hybrid Search", key="hybrid_search_btn", type="primary", use_container_width=True):
        ingredients = [i.strip() for i in ingredients_input.split('\n') if i.strip()]
        
        if ingredients or query:
            with st.spinner("Searching..."):
                results, warning = hybrid_search_recipes(ingredients, query, limit)
                
                if warning:
                    st.warning(warning)
                
                if results:
                    st.success(f"Found {len(results)} matching recipes")
                    _display_search_results(results)
                else:
                    st.info("No matching recipes found. Try different ingredients or query.")
        else:
            st.warning("Please enter ingredients or a search query")


def _display_recipe_list(recipes):
    """Display a list of recipe names with expandable details"""
    for recipe_name in recipes:
        with st.container():
            st.markdown(f'<div class="cookable-recipe">{recipe_name}</div>', unsafe_allow_html=True)
            
            details = fetch_recipe_details(recipe_name)
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
                        st.image(details['imageUrl'], caption=recipe_name)


def _display_search_results(results):
    """Display search results with scores"""
    for result in results:
        recipe_name = result.get('recipeName', 'Unknown')
        score = result.get('score', 0)
        match_type = result.get('matchType', 'semantic')
        
        # Format score as percentage
        score_pct = f"{score * 100:.0f}%" if score else "N/A"
        
        # Match type emoji
        match_emoji = "🎯" if match_type == "exact" else "🧠" if match_type == "semantic" else "🔗"
        
        with st.expander(f"{match_emoji} **{recipe_name}** — {score_pct} match"):
            details = fetch_recipe_details(recipe_name)
            if details:
                col1, col2 = st.columns(2)
                with col1:
                    st.markdown("**Ingredients:**")
                    for ing in details.get('ingredients', []):
                        st.markdown(f"- {ing}")
                with col2:
                    st.markdown("**Instructions:**")
                    instructions = details.get('instructions', 'No instructions available.')
                    st.write(instructions[:500] + "..." if len(instructions) > 500 else instructions)
            else:
                st.info("Could not load recipe details")
