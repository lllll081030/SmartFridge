"""
Semantic Search View - Search recipes using AI-powered semantic similarity
"""
import streamlit as st
from api import (
    search_recipes_semantic, 
    hybrid_search_recipes, 
    fetch_recipe_details,
    get_search_stats,
    index_all_recipes,
    seed_ingredient_aliases,
    get_ingredient_aliases,
    generate_ingredient_aliases
)


def render():
    """Render the Semantic Search page"""
    st.markdown('<p class="section-header">🔍 Smart Recipe Search</p>', unsafe_allow_html=True)
    
    # Tabs for different search modes
    tab1, tab2, tab3 = st.tabs(["🔎 Semantic Search", "🔗 Hybrid Search", "⚙️ Admin"])
    
    with tab1:
        _render_semantic_search()
    
    with tab2:
        _render_hybrid_search()
    
    with tab3:
        _render_admin_panel()


def _render_semantic_search():
    """Render semantic search UI"""
    st.markdown("### Search by meaning, not just keywords")
    st.info("💡 Try searching for 'quick Italian dinner' or 'healthy breakfast ideas'")
    
    col1, col2 = st.columns([4, 1])
    with col1:
        query = st.text_input("Search recipes...", key="semantic_query", placeholder="e.g., 'spicy Asian noodles'")
    with col2:
        limit = st.number_input("Results", min_value=1, max_value=20, value=10, key="semantic_limit")
    
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
                    st.info("No recipes found. Try indexing your recipes first in the Admin tab.")
        else:
            st.warning("Please enter a search query")


def _render_hybrid_search():
    """Render hybrid search UI (combining exact + semantic)"""
    st.markdown("### Search using ingredients AND natural language")
    st.info("💡 Combine your available ingredients with a description of what you want")
    
    # Ingredient input
    ingredients_input = st.text_area(
        "Enter ingredients (one per line):",
        height=100,
        key="hybrid_ingredients",
        placeholder="tomato\nchicken\ngarlic"
    )
    
    # Query input
    query = st.text_input(
        "Additional description (optional):",
        key="hybrid_query",
        placeholder="e.g., 'something quick and healthy'"
    )
    
    col1, col2 = st.columns(2)
    with col1:
        limit = st.number_input("Max results", min_value=1, max_value=20, value=10, key="hybrid_limit")
    with col2:
        threshold = st.slider(
            "Min match %",
            min_value=0,
            max_value=100,
            value=0,
            step=5,
            key="hybrid_threshold",
            help="Filter out recipes with match score below this threshold"
        )
    
    if st.button("🔍 Hybrid Search", key="hybrid_search_btn", type="primary"):
        ingredients = [i.strip() for i in ingredients_input.split('\n') if i.strip()]
        
        if ingredients or query:
            with st.spinner("Searching..."):
                score_threshold = threshold / 100.0
                results, warning = hybrid_search_recipes(ingredients, query, limit, score_threshold)
                
                if warning:
                    st.warning(warning)
                
                if results:
                    st.success(f"Found {len(results)} matching recipes (min {threshold}% match)")
                    _display_search_results(results)
                else:
                    if threshold > 0:
                        st.info(f"No recipes found with ≥{threshold}% match. Try lowering the threshold.")
                    else:
                        st.info("No recipes found matching your criteria.")
        else:
            st.warning("Please enter ingredients or a search query")


def _display_search_results(results):
    """Display search results with expandable details"""
    for i, result in enumerate(results):
        recipe_name = result.get('recipeName', 'Unknown')
        score = result.get('score', 0)
        cuisine = result.get('cuisineType', '')
        match_type = result.get('matchType', 'semantic')
        
        # Format score as percentage
        score_pct = f"{score * 100:.1f}%" if score else "N/A"
        
        # Match type badge - V2.3 adds hybrid_rrf for true hybrid search
        if match_type == "exact":
            match_badge = "🎯"
        elif match_type == "hybrid_rrf":
            match_badge = "⚡"  # Lightning for hybrid RRF fusion
        elif match_type == "semantic":
            match_badge = "🧠"
        elif match_type == "ingredient":
            match_badge = "🥗"
        else:
            match_badge = "🔗"
        
        with st.expander(f"{match_badge} **{recipe_name}** - {score_pct} match ({cuisine})"):
            # Fetch and display recipe details
            details = fetch_recipe_details(recipe_name)
            if details:
                col1, col2 = st.columns(2)
                with col1:
                    st.markdown("**Ingredients:**")
                    for ing in details.get('ingredients', []):
                        st.markdown(f"- {ing}")
                with col2:
                    st.markdown("**Instructions:**")
                    st.write(details.get('instructions', 'No instructions available.'))
            else:
                st.info("Could not load recipe details")


def _render_admin_panel():
    """Render admin panel for managing search index and aliases"""
    st.markdown("### Search Administration")
    
    # Stats
    st.markdown("#### Current Status")
    stats = get_search_stats()
    
    col1, col2, col3 = st.columns(3)
    with col1:
        status = "✅ Active" if stats.get('initialized', False) else "❌ Not Ready"
        st.metric("Vector Search", status)
    with col2:
        embedding_status = "✅ Available" if stats.get('embeddingAvailable', False) else "❌ Unavailable"
        st.metric("Embeddings (OpenAI)", embedding_status)
    with col3:
        points = stats.get('pointsCount', 0)
        st.metric("Indexed Recipes", points)
    
    if stats.get('error'):
        st.error(f"Error: {stats['error']}")
    
    st.markdown("---")
    
    # Index recipes
    st.markdown("#### Index Recipes")
    st.info("Index all your recipes for semantic search. Run this after adding new recipes.")
    
    if st.button("📊 Index All Recipes", key="index_btn"):
        with st.spinner("Indexing recipes... This may take a moment."):
            result = index_all_recipes()
            if result.get('error'):
                st.error(result['error'])
            else:
                st.success(f"Indexed {result.get('count', 0)} recipes!")
    
    st.markdown("---")
    
    # Ingredient aliases
    st.markdown("#### Ingredient Aliases")
    st.info("Aliases help match similar ingredients (e.g., 'roma tomato' → 'tomato')")
    
    if st.button("🌱 Seed Common Aliases", key="seed_btn"):
        with st.spinner("Seeding aliases..."):
            result = seed_ingredient_aliases()
            if result.get('error'):
                st.error(result['error'])
            else:
                st.success("Seeded common ingredient aliases!")
    
    # Look up aliases
    st.markdown("##### Check Ingredient Aliases")
    ingredient = st.text_input("Enter ingredient name:", key="alias_lookup")
    
    col1, col2 = st.columns(2)
    with col1:
        if st.button("Look Up Aliases", key="lookup_btn"):
            if ingredient:
                result = get_ingredient_aliases(ingredient)
                st.write(f"**Canonical:** {result.get('canonical', ingredient)}")
                aliases = result.get('aliases', [])
                if aliases:
                    st.write(f"**Aliases:** {', '.join(aliases)}")
                else:
                    st.write("No aliases found")
    
    with col2:
        if st.button("🤖 Generate AI Aliases", key="generate_alias_btn"):
            if ingredient:
                with st.spinner("Generating with AI..."):
                    result = generate_ingredient_aliases(ingredient)
                    generated = result.get('generated', [])
                    if generated:
                        st.success(f"Generated {len(generated)} aliases: {', '.join(generated)}")
                    else:
                        st.warning("Could not generate aliases. Is OpenAI API key configured?")
