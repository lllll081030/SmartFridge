"""AI Recipe Parser page module"""
import streamlit as st
from ollama_client import ollama
from api import add_recipe


def render():
    """Render the AI Recipe Parser page"""
    st.markdown('<p class="section-header">🤖 AI Recipe Parser</p>', unsafe_allow_html=True)
    
    st.markdown("""
    Paste any recipe from the web and let AI extract the details automatically!
    Perfect for quickly adding recipes to your collection.
    """)
    
    # Check if Ollama is available
    if not ollama.is_available():
        st.error("""
        ⚠️ **Ollama is not running or not installed**
        
        Please make sure Ollama is installed and running. 
        The llama3.2 model should be downloaded.
        """)
        return
    
    # Tab selection
    tab1, tab2 = st.tabs(["📝 Paste Recipe Text", "ℹ️ How to Use"])
    
    with tab1:
        # Recipe text input
        recipe_text = st.text_area(
            "Paste recipe text here",
            height=300,
            placeholder="""Example:
Spaghetti Carbonara

Ingredients:
- 400g spaghetti
- 200g pancetta or bacon
- 4 eggs
- 100g parmesan cheese
- Salt and black pepper
- 2 cloves garlic

Instructions:
1. Cook spaghetti according to package directions
2. Fry pancetta until crispy
3. Mix eggs and parmesan in a bowl
4. Combine hot pasta with pancetta
5. Add egg mixture and toss quickly
6. Season with salt and pepper
""",
            key="recipe_input"
        )
        
        col1, col2 = st.columns([1, 3])
        
        with col1:
            parse_button = st.button("🤖 Parse Recipe", type="primary", use_container_width=True)
        
        with col2:
            if st.button("🗑️ Clear", use_container_width=True):
                # Clear the parsed recipe from session state
                if 'parsed_recipe' in st.session_state:
                    del st.session_state.parsed_recipe
                # Note: Can't modify recipe_input after widget creation
                # User needs to manually clear or refresh page
                st.rerun()
        
        if parse_button and recipe_text.strip():
            with st.spinner("🤖 AI is analyzing the recipe... ⏱️ First run may take 1-2 minutes..."):
                import time
                start_time = time.time()
                
                parsed = ollama.parse_recipe(recipe_text)
                
                elapsed = time.time() - start_time
                
                if parsed:
                    st.session_state.parsed_recipe = parsed
                    st.success(f"✅ Recipe parsed successfully in {elapsed:.1f} seconds!")
                    st.rerun()
                else:
                    st.error(f"❌ Failed to parse recipe after {elapsed:.1f} seconds. Please try again.")
                    st.info("💡 If this keeps failing, try:\n- Restarting Ollama\n- Using a simpler recipe format\n- Checking the terminal for error details")
    
    with tab2:
        st.markdown("""
        ### 📖 How to Use
        
        1. **Find a recipe** on any cooking website
        2. **Copy the entire recipe** (ingredients + instructions)
        3. **Paste it** in the text area
        4. **Click "Parse Recipe"** and wait for AI to process it
        5. **Review and edit** the extracted information
        6. **Save** to your recipe book!
        
        ### ✨ What AI Extracts
        - 📋 Recipe name
        - 🌍 Cuisine type
        - 🥘 Ingredients with quantities
        - 👨‍🍳 Step-by-step instructions
        - 🧂 Automatically categorizes seasonings vs main ingredients
        
        ### 💡 Tips
        - Works best with clearly formatted recipes
        - Include both ingredients AND instructions
        - You can edit everything after parsing
        - First run may take longer (model loading)
        """)
    
    # Display parsed recipe for review/editing
    if 'parsed_recipe' in st.session_state and st.session_state.parsed_recipe:
        st.markdown("---")
        display_parsed_recipe()


def display_parsed_recipe():
    """Display parsed recipe with edit controls"""
    parsed = st.session_state.parsed_recipe
    
    st.markdown("### ✅ Parsed Recipe - Review & Edit")
    
    # Recipe name
    recipe_name = st.text_input(
        "Recipe Name",
        value=parsed.get("name", ""),
        key="edit_name"
    )
    
    # Cuisine type
    cuisine_options = ["ITALIAN", "CHINESE", "JAPANESE", "MEXICAN", "AMERICAN", 
                      "FRENCH", "INDIAN", "THAI", "MEDITERRANEAN", "OTHER"]
    
    current_cuisine = parsed.get("cuisine", "OTHER").upper()
    if current_cuisine not in cuisine_options:
        current_cuisine = "OTHER"
    
    cuisine_type = st.selectbox(
        "Cuisine Type",
        options=cuisine_options,
        index=cuisine_options.index(current_cuisine) if current_cuisine in cuisine_options else 0,
        key="edit_cuisine"
    )
    
    # Ingredients section
    st.markdown("#### 🥘 Ingredients")
    
    st.caption("""
    💡 **Quantity**: Use exact count for items (3 eggs), or "1" for measured ingredients (flour, milk).  
    🧂 **Seasoning**: Check this for spices/seasonings. Seasonings are NOT saved as recipe requirements (assumed always available).
    """)
    
    ingredients = parsed.get("ingredients", [])
    
    if not ingredients:
        st.warning("No ingredients were extracted. Please check the input.")
    
    # Separate main ingredients from seasonings
    main_ingredients = [ing for ing in ingredients if not ing.get("isSeasoning", False)]
    seasonings = [ing for ing in ingredients if ing.get("isSeasoning", False)]
    
    # Display main ingredients
    if main_ingredients:
        st.markdown("**Main Ingredients** (required):")
    else:
        st.warning("⚠️ No main ingredients! At least one non-seasoning ingredient is required.")
    
    # Display ingredients with edit/remove options
    items_to_remove = []
    
    # Show header row for main ingredients
    if main_ingredients:
        col1, col2, col3, col4 = st.columns([3, 2, 1, 1])
        with col1:
            st.caption("**Name**")
        with col2:
            st.caption("**Qty**")
        with col3:
            st.caption("**Mark as Seasoning**")
        with col4:
            st.caption("")
    
    # Display main ingredients
    for idx, ing in enumerate(ingredients):
        if ing.get("isSeasoning", False):
            continue  # Skip seasonings in this section
            
        col1, col2, col3, col4 = st.columns([3, 2, 1, 1])
        
        with col1:
            name = st.text_input(
                "Ingredient",
                value=ing.get("name", ""),
                key=f"ing_name_{idx}",
                label_visibility="collapsed"
            )
            ingredients[idx]["name"] = name
        
        with col2:
            quantity = st.text_input(
                "Quantity",
                value=ing.get("quantity", ""),
                key=f"ing_qty_{idx}",
                label_visibility="collapsed"
            )
            ingredients[idx]["quantity"] = quantity
        
        with col3:
            is_seasoning = st.checkbox(
                "Seasoning",
                value=False,
                key=f"ing_seasoning_{idx}",
                help="Check to move to seasonings"
            )
            ingredients[idx]["isSeasoning"] = is_seasoning
        
        with col4:
            if st.button("🗑️", key=f"remove_ing_{idx}", help="Remove"):
                items_to_remove.append(idx)
    
    # Display seasonings section
    if seasonings:
        st.markdown("---")
        st.markdown("**Seasonings** (optional - not saved as requirements):")
        
        col1, col2, col3, col4 = st.columns([3, 2, 1, 1])
        with col1:
            st.caption("**Name**")
        with col2:
            st.caption("**Qty**")
        with col3:
            st.caption("**Unmark**")
        with col4:
            st.caption("")
    
    for idx, ing in enumerate(ingredients):
        if not ing.get("isSeasoning", False):
            continue  # Skip non-seasonings in this section
            
        col1, col2, col3, col4 = st.columns([3, 2, 1, 1])
        
        with col1:
            name = st.text_input(
                "Ingredient",
                value=ing.get("name", ""),
                key=f"seas_name_{idx}",
                label_visibility="collapsed"
            )
            ingredients[idx]["name"] = name
        
        with col2:
            quantity = st.text_input(
                "Quantity",
                value=ing.get("quantity", ""),
                key=f"seas_qty_{idx}",
                label_visibility="collapsed"
            )
            ingredients[idx]["quantity"] = quantity
        
        with col3:
            is_seasoning = st.checkbox(
                "Seasoning",
                value=True,
                key=f"seas_seasoning_{idx}",
                help="Uncheck to move to main ingredients"
            )
            ingredients[idx]["isSeasoning"] = is_seasoning
        
        with col4:
            if st.button("🗑️", key=f"remove_seas_{idx}", help="Remove"):
                items_to_remove.append(idx)
    
    # Remove marked ingredients
    if items_to_remove:
        for idx in sorted(items_to_remove, reverse=True):
            ingredients.pop(idx)
        st.rerun()
    
    # Add ingredient button
    if st.button("➕ Add Ingredient"):
        ingredients.append({"name": "", "quantity": "", "isSeasoning": False})
        st.rerun()
    
    # Instructions section
    st.markdown("#### 📝 Instructions")
    
    instructions = parsed.get("instructions", [])
    
    if not instructions:
        st.warning("No instructions were extracted.")
    
    # Display instructions
    instructions = parsed.get("instructions", [])
    
    # Handle both array and string formats
    if isinstance(instructions, str):
        # If it's a string, split by newline
        instruction_steps = [step.strip() for step in instructions.split('\n') if step.strip()]
    elif isinstance(instructions, list):
        # If it's already a list, use it
        instruction_steps = instructions
    else:
        instruction_steps = []
    
    # Format with numbering
    instructions_text = "\n".join([f"{i+1}. {step}" for i, step in enumerate(instruction_steps)])
    
    edited_instructions = st.text_area(
        "Cooking Instructions (one step per line)",
        value=instructions_text,
        height=200,
        key="edit_instructions"
    )
    
    # Save button
    st.markdown("---")
    col1, col2, col3 = st.columns([1, 2, 1])
    
    with col2:
        if st.button("💾 Save Recipe to Book", type="primary", use_container_width=True):
            # Check if recipe already exists
            from api import fetch_recipes_by_cuisine
            all_recipes = fetch_recipes_by_cuisine()
            
            # Get all existing recipe names (flatten the dict values)
            existing_names = []
            for cuisine_recipes in all_recipes.values():
                existing_names.extend([r["name"].lower() for r in cuisine_recipes])
            
            if recipe_name.lower() in existing_names:
                st.error(f"❌ Recipe '{recipe_name}' already exists in your recipe book!")
                st.info("💡 Try editing the recipe name or delete the old one first.")
            else:
                # Separate ingredients from seasonings
                main_ingredients = [
                    ing["name"].lower() 
                    for ing in ingredients 
                    if ing["name"].strip() and not ing.get("isSeasoning", False)
                ]
                
                seasoning_list = [
                    ing["name"].lower()
                    for ing in ingredients
                    if ing["name"].strip() and ing.get("isSeasoning", False)
                ]
                
                if not main_ingredients:
                    st.warning("⚠️ No main ingredients found! Please add at least one non-seasoning ingredient.")
                else:
                    # Parse instructions back to list
                    instruction_list = [
                        line.strip().lstrip("0123456789. ")  # Remove numbering
                        for line in edited_instructions.split("\n")
                        if line.strip()
                    ]
                    
                    instructions_final = "\n".join(instruction_list)
                    
                    # Save recipe with separate ingredients and seasonings
                    success = add_recipe(
                        name=recipe_name.lower(),
                        ingredients=main_ingredients,
                        cuisine_type=cuisine_type,
                        instructions=instructions_final,
                        seasonings=seasoning_list  # New parameter
                    )
                    
                    if success:
                        st.success(f"✅ '{recipe_name}' saved with {len(main_ingredients)} ingredients and {len(seasoning_list)} seasonings!")
                        
                        # Clear parsed recipe (can't clear text input due to Streamlit limitation)
                        del st.session_state.parsed_recipe
                        
                        import time
                        time.sleep(1)
                        
                        # Navigate to recipe book
                        st.session_state.current_page = 'recipes'
                        st.rerun()
                    else:
                        st.error("❌ Failed to save recipe. Please try again.")
