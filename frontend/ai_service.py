"""
AI Service for Ingredient Substitutions
Flask microservice that provides AI-powered ingredient substitution suggestions
"""
from flask import Flask, request, jsonify
from flask_cors import CORS
from ollama_client import ollama
import json

app = Flask(__name__)
CORS(app)  # Allow requests from Spring Boot backend

@app.route('/ai/substitutions', methods=['POST'])
def get_substitutions():
    """
    Generate AI-powered ingredient substitution suggestions
    
    Request JSON:
    {
        "ingredient": "tomato",
        "cuisine": "ITALIAN",
        "recipeIngredients": ["pasta", "tomato", "basil"],
        "fridgeSupplies": ["pasta", "basil", "bell pepper"]
    }
    
    Response JSON:
    {
        "substitutes": [
            {
                "ingredient": "canned tomato",
                "inFridge": false,
                "confidence": 0.9,
                "reasoning": "Canned tomatoes work well in Italian dishes..."
            },
            ...
        ]
    }
    """
    print("\n" + "="*60)
    print("[AI SERVICE] Received substitution request")
    print("="*60)
    
    try:
        data = request.get_json()
        print(f"[DEBUG] Request data: {data}")
        
        ingredient = data.get('ingredient', '')
        cuisine = data.get('cuisine', 'OTHER')
        recipe_ingredients = data.get('recipeIngredients', [])
        fridge_supplies = data.get('fridgeSupplies', [])
        
        print(f"[DEBUG] Ingredient: {ingredient}")
        print(f"[DEBUG] Cuisine: {cuisine}")
        print(f"[DEBUG] Recipe ingredients: {recipe_ingredients}")
        print(f"[DEBUG] Fridge supplies: {fridge_supplies}")
        
        if not ingredient:
            print("[ERROR] Missing ingredient parameter")
            return jsonify({"error": "Missing ingredient parameter"}), 400
        
        # Check if Ollama is available
        print("[DEBUG] Checking if Ollama is available...")
        if not ollama.is_available():
            print("[ERROR] AI service (Ollama) is not available")
            return jsonify({
                "error": "AI service (Ollama) is not available",
                "substitutes": []
            }), 503
        
        print("[DEBUG] Ollama is available, generating substitutions...")
        
        # Generate substitution suggestions using AI
        substitutes = generate_substitution_suggestions(
            ingredient,
            cuisine,
            recipe_ingredients,
            fridge_supplies
        )
        
        print(f"[SUCCESS] Generated {len(substitutes)} substitution suggestions")
        print(f"[DEBUG] Response: {substitutes}")
        
        return jsonify({"substitutes": substitutes})
        
    except Exception as e:
        print(f"[ERROR] Failed to generate substitutions: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({
            "error": str(e),
            "substitutes": []
        }), 500


def generate_substitution_suggestions(ingredient, cuisine, recipe_ingredients, fridge_supplies):
    """
    Use Ollama to generate ingredient substitution suggestions
    """
    # Build context-aware prompt - STRICT: ONLY suggest items from fridge
    fridge_list = ', '.join(fridge_supplies[:20]) if fridge_supplies else 'EMPTY FRIDGE'
    
    prompt = f"""You are a professional chef. A user wants to cook a {cuisine} recipe but is missing "{ingredient}".

CRITICAL RULE: You can ONLY suggest substitutes from the user's fridge. Do NOT suggest items not listed below!

User's Fridge (ONLY suggest from these):
{fridge_list}

Recipe Context:
- Cuisine: {cuisine}
- Other ingredients: {', '.join(recipe_ingredients[:10])}

Task: Find 1-3 items from the fridge above that can substitute for "{ingredient}".

Return ONLY valid JSON in this exact format:
{{
  "substitutes": [
    {{
      "ingredient": "exact name from fridge list",
      "inFridge": true,
      "confidence": 0.0 to 1.0,
      "reasoning": "why this fridge item works"
    }}
  ]
}}

RULES:
- ONLY use ingredients from the fridge list above
- If no good substitutes exist in fridge, return empty array []
- Confidence: 1.0 = perfect, 0.7 = good, 0.5 = acceptable
- Keep reasoning under 40 words
- Return 1-3 substitutes max

Return ONLY valid JSON, no markdown."""

    print(f"[DEBUG] Requesting substitutions for '{ingredient}' from Ollama...")
    print(f"[DEBUG] Fridge has {len(fridge_supplies)} items: {fridge_supplies}")
    
    try:
        response = ollama.generate(prompt, format_json=True)
        
        print(f"[DEBUG] Ollama raw response: {response}")
        
        if not response:
            print(f"[ERROR] Ollama returned empty response")
            return []
        
        # Parse JSON response
        try:
            parsed = json.loads(response)
            print(f"[DEBUG] Parsed JSON: {parsed}")
            
            substitutes = parsed.get('substitutes', [])
            print(f"[DEBUG] Found {len(substitutes)} substitutes in response")
            
            # Validate and clean each substitute
            cleaned_substitutes = []
            for idx, sub in enumerate(substitutes):
                print(f"[DEBUG] Processing substitute {idx + 1}: {sub}")
                
                if not isinstance(sub, dict):
                    print(f"[WARNING] Substitute {idx + 1} is not a dict, skipping")
                    continue
                
                ingredient_name = sub.get('ingredient', '').strip()
                if not ingredient_name:
                    print(f"[WARNING] Substitute {idx + 1} has no ingredient name, skipping")
                    continue
                
                print(f"[DEBUG] Ingredient name: '{ingredient_name}'")
                
                # Validate confidence
                confidence = sub.get('confidence', 0.5)
                if isinstance(confidence, str):
                    try:
                        confidence = float(confidence)
                    except:
                        confidence = 0.5
                confidence = max(0.0, min(1.0, confidence))  # Clamp to [0, 1]
                
                # Check if actually in fridge (case-insensitive)
                fridge_lower = [item.lower() for item in fridge_supplies]
                in_fridge = ingredient_name.lower() in fridge_lower
                
                print(f"[DEBUG] Is '{ingredient_name}' in fridge? {in_fridge}")
                
                # STRICT FILTER: Only include if it's actually in the fridge
                if not in_fridge:
                    print(f"[WARNING] '{ingredient_name}' is NOT in fridge, skipping!")
                    continue
                
                reasoning = sub.get('reasoning', 'Suitable alternative')[:200]  # Limit length
                
                cleaned_substitutes.append({
                    "ingredient": ingredient_name,
                    "inFridge": True,  # Always true now since we filtered
                    "confidence": round(confidence, 2),
                    "reasoning": reasoning
                })
                
                print(f"[SUCCESS] Added substitute: {ingredient_name} (confidence: {confidence})")
            
            # Sort by confidence (highest first)
            cleaned_substitutes.sort(key=lambda x: x['confidence'], reverse=True)
            
            print(f"[SUCCESS] Returning {len(cleaned_substitutes)} substitution suggestions")
            return cleaned_substitutes[:3]  # Return top 3 from fridge only
            
        except json.JSONDecodeError as e:
            print(f"[ERROR] Failed to parse JSON: {e}")
            print(f"[DEBUG] Raw response: {response[:500]}")
            return []
            
    except Exception as e:
        print(f"[ERROR] Ollama request failed: {e}")
        import traceback
        traceback.print_exc()
        return []


@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    ollama_available = ollama.is_available()
    return jsonify({
        "status": "healthy",
        "ollama_available": ollama_available,
        "model": ollama.model
    })


@app.route('/ai/parse-recipe', methods=['POST'])
def parse_recipe():
    """
    Parse recipe text using AI to extract structured data
    
    Request JSON:
    {
        "recipeText": "Recipe: Pancakes\nIngredients: flour, milk, egg..."
    }
    
    Response JSON:
    {
        "success": true,
        "recipe": {
            "name": "Pancakes",
            "cuisine": "AMERICAN",
            "ingredients": [...],
            "instructions": [...]
        }
    }
    """
    print("\n" + "="*60)
    print("[AI SERVICE] Received recipe parsing request")
    print("="*60)
    
    try:
        data = request.get_json()
        recipe_text = data.get('recipeText', '')
        
        print(f"[DEBUG] Recipe text length: {len(recipe_text)} characters")
        
        if not recipe_text or not recipe_text.strip():
            print("[ERROR] Missing recipe text")
            return jsonify({
                "success": False,
                "error": "Recipe text is required"
            }), 400
        
        # Check if Ollama is available
        print("[DEBUG] Checking if Ollama is available...")
        if not ollama.is_available():
            print("[ERROR] Ollama is not available")
            return jsonify({
                "success": False,
                "error": "AI service (Ollama) is not available"
            }), 503
        
        print("[DEBUG] Ollama is available, parsing recipe...")
        
        # Parse recipe using Ollama
        parsed_recipe = ollama.parse_recipe(recipe_text)
        
        if parsed_recipe:
            print(f"[SUCCESS] Successfully parsed recipe: {parsed_recipe.get('name', 'Unknown')}")
            print(f"[DEBUG] Ingredients count: {len(parsed_recipe.get('ingredients', []))}")
            return jsonify({
                "success": True,
                "recipe": parsed_recipe
            })
        else:
            print("[ERROR] Failed to parse recipe")
            return jsonify({
                "success": False,
                "error": "Failed to parse recipe. Please check the format."
            }), 400
        
    except Exception as e:
        print(f"[ERROR] Exception while parsing recipe: {e}")
        import traceback
        traceback.print_exc()
        return jsonify({
            "success": False,
            "error": str(e)
        }), 500


if __name__ == '__main__':
    print("=" * 60)
    print("Starting AI Substitution Service on http://localhost:5001")
    print("=" * 60)
    print(f"Ollama available: {ollama.is_available()}")
    print(f"Using model: {ollama.model}")
    print()
    print("Available endpoints:")
    print("  POST /ai/substitutions - Get ingredient substitutions")
    print("  POST /ai/parse-recipe - Parse recipe text")
    print("  GET  /health - Health check")
    print()
    
    app.run(host='0.0.0.0', port=5001, debug=True)
