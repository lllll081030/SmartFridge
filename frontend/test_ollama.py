"""
Test script for Ollama integration
"""
from ollama_client import ollama

print("Testing Ollama connection...")
print(f"Ollama available: {ollama.is_available()}")

if ollama.is_available():
    print("\nTesting simple generation...")
    response = ollama.generate("What is 2+2? Answer with just the number.")
    print(f"Response: {response}")
    
    print("\nTesting recipe parsing...")
    sample_recipe = """
    Scrambled Eggs
    
    Ingredients:
    - 3 eggs
    - 2 tablespoons milk
    - Salt and pepper to taste
    - 1 tablespoon butter
    
    Instructions:
    1. Beat eggs with milk in a bowl
    2. Melt butter in a pan over medium heat
    3. Pour in egg mixture
    4. Stir gently until cooked
    5. Season with salt and pepper
    """
    
    parsed = ollama.parse_recipe(sample_recipe)
    
    if parsed:
        print("✅ Recipe parsing successful!")
        print(f"Name: {parsed.get('name')}")
        print(f"Cuisine: {parsed.get('cuisine')}")
        print(f"Ingredients: {len(parsed.get('ingredients', []))} items")
        print(f"Instructions: {len(parsed.get('instructions', []))} steps")
    else:
        print("❌ Recipe parsing failed")
else:
    print("❌ Ollama is not available. Please start Ollama.")
