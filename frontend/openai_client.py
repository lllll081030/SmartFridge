"""
OpenAI Client - Interface for OpenAI API (replaces Ollama)
"""
import os
import json
import re
from typing import Dict, List, Optional
from openai import OpenAI
from dotenv import load_dotenv

# Load .env from project root or current dir
load_dotenv()
load_dotenv(os.path.join(os.path.dirname(__file__), '..', '.env'))


class OpenAIClient:
    """Client for interacting with OpenAI API"""

    def __init__(self, api_key: str = None):
        self.api_key = api_key or os.environ.get("OPENAI_API_KEY", "")
        self.chat_model = os.environ.get("OPENAI_CHAT_MODEL", "gpt-4o-mini")
        self.client = OpenAI(api_key=self.api_key)

    def is_available(self) -> bool:
        """Check if OpenAI API is reachable"""
        try:
            self.client.models.list()
            return True
        except Exception:
            return False

    def generate(self, prompt: str, format_json: bool = False) -> str:
        """
        Generate text using OpenAI Chat Completions API

        Args:
            prompt: The prompt to send to the model
            format_json: If True, request JSON formatted response

        Returns:
            Generated text response
        """
        try:
            kwargs = {
                "model": self.chat_model,
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": 2048,
            }

            if format_json:
                kwargs["response_format"] = {"type": "json_object"}

            response = self.client.chat.completions.create(**kwargs)
            return response.choices[0].message.content or ""

        except Exception as e:
            print(f"[ERROR] Error calling OpenAI: {e}")
            return None

    def parse_recipe(self, recipe_text: str) -> Optional[Dict]:
        """
        Parse recipe text using OpenAI

        Args:
            recipe_text: Raw recipe text to parse

        Returns:
            Dictionary with parsed recipe data or None if parsing failed
        """
        prompt = f"""You are a recipe ingredient extractor. Parse the recipe below and extract ALL ingredients.

RECIPE TEXT:
\"\"\"
{recipe_text}
\"\"\"

Return this JSON:
{{
  "name": "<actual recipe name from text>",
  "cuisine": "<Chinese/Italian/American/Japanese/Korean/French/Mexican/Indian/OTHER>",
  "ingredients": [
    {{"name": "milk", "quantity": "1", "isSeasoning": false}},
    {{"name": "egg", "quantity": "1", "isSeasoning": false}},
    {{"name": "salt", "quantity": "1", "isSeasoning": true}}
  ],
  "instructions": ["step 1", "step 2"]
}}

RULES:
1. List EVERY ingredient mentioned in the recipe - do not skip any!
2. "name" = ingredient name ONLY (no amounts like "500g" or "2 cups")
3. "quantity" = always "1"
4. isSeasoning = true for: salt, pepper, oil, spices, sauces, sugar, honey, vinegar
5. isSeasoning = false for: meat, vegetables, flour, eggs, milk, butter, fruits
6. Extract the REAL recipe name, not a placeholder

Return ONLY valid JSON."""

        print(f"[DEBUG] Sending recipe to OpenAI...")
        response = self.generate(prompt, format_json=True)

        if not response:
            print(f"[ERROR] OpenAI returned no response")
            return None

        print(f"[DEBUG] Received response: {response}")

        if response:
            try:
                # Try to parse JSON directly
                parsed = json.loads(response)
                print(f"[SUCCESS] Parsed JSON successfully")

                # Fix recipe name field (handle both "name" and "recipe name")
                if "recipe name" in parsed and "name" not in parsed:
                    parsed["name"] = parsed.pop("recipe name")

                # Post-process ingredients
                if "ingredients" in parsed:
                    flat_ingredients = []

                    for ing in parsed["ingredients"]:
                        # Handle nested "ingredients" arrays (malformed response)
                        if isinstance(ing, dict) and "ingredients" in ing and isinstance(ing["ingredients"], list):
                            flat_ingredients.extend(ing["ingredients"])
                        else:
                            flat_ingredients.append(ing)

                    # Now clean each ingredient
                    cleaned = []
                    for ing in flat_ingredients:
                        # Handle plain string ingredients
                        if isinstance(ing, str):
                            ing = {"name": ing, "quantity": "1", "isSeasoning": False}
                        elif not isinstance(ing, dict):
                            continue

                        # 1. Clean ingredient name - remove quantities/units
                        name = str(ing.get("name", "")).strip()

                        # Remove patterns like "500g", "2 tbsp", numbers at end
                        name = re.sub(
                            r'\s*\d+\.?\d*\s*(g|kg|ml|l|cup|cups|tbsp|tsp|oz|lb|gram|liter|ounce|pound|tablespoon|teaspoon|piece|pieces|small|large|handful)s?\b',
                            '', name, flags=re.IGNORECASE)
                        name = re.sub(r'\s*\d+\.?\d*/?\d*\s*$', '', name)  # Remove trailing fractions/numbers
                        name = re.sub(r'^\d+\.?\d*\s*', '', name)  # Remove leading numbers
                        name = re.sub(r'\s*,.*$', '', name)  # Remove trailing commas and text after
                        name = name.strip()

                        if not name or name.lower() in ['spices', 'aromatics', 'spices and aromatics']:
                            continue  # Skip category headers

                        # 2. Set quantity to "1" always (simplified)
                        qty = "1"

                        # 3. Determine if seasoning
                        seasoning_keywords = ['salt', 'pepper', 'sugar', 'oil', 'sauce', 'vinegar',
                                              'spice', 'cumin', 'paprika', 'oregano', 'basil', 'thyme',
                                              'bay leaf', 'star anise', 'cinnamon', 'clove', 'ginger',
                                              'peppercorn', 'fennel', 'paste', 'fermented', 'peanut butter']
                        is_seas = ing.get("isSeasoning", False)
                        if isinstance(is_seas, str):
                            is_seas = is_seas.lower() in ['true', '1', 'yes']

                        # Auto-detect seasonings from name
                        name_lower = name.lower()
                        if any(kw in name_lower for kw in seasoning_keywords):
                            is_seas = True

                        cleaned.append({
                            "name": name,
                            "quantity": qty,
                            "isSeasoning": bool(is_seas)
                        })

                    parsed["ingredients"] = cleaned

                print(f"[DEBUG] Processed {len(parsed.get('ingredients', []))} ingredients")
                return parsed
            except json.JSONDecodeError as e:
                print(f"[WARNING] Direct JSON parse failed: {e}")

                # If response has markdown code blocks, try to extract JSON
                if "```json" in response:
                    print(f"[DEBUG] Attempting to extract from ```json block")
                    start = response.find("```json") + 7
                    end = response.find("```", start)
                    json_str = response[start:end].strip()
                    try:
                        parsed = json.loads(json_str)
                        print(f"[SUCCESS] Extracted JSON from markdown block")
                        return parsed
                    except json.JSONDecodeError as e2:
                        print(f"[ERROR] Failed to parse extracted JSON: {e2}")
                elif "```" in response:
                    print(f"[DEBUG] Attempting to extract from ``` block")
                    start = response.find("```") + 3
                    end = response.find("```", start)
                    json_str = response[start:end].strip()
                    try:
                        parsed = json.loads(json_str)
                        print(f"[SUCCESS] Extracted JSON from code block")
                        return parsed
                    except json.JSONDecodeError as e2:
                        print(f"[ERROR] Failed to parse extracted JSON: {e2}")

                print(f"[ERROR] Could not extract valid JSON. Full response:")
                print(response)
                return None

        return None


# Global instance
openai_client = OpenAIClient()
