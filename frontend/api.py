"""API client functions for SmartFridge backend"""
import streamlit as st
import requests
import time
from config import API_URL


def fetch_fridge():
    """Fetch current fridge contents from backend"""
    try:
        response = requests.get(f"{API_URL}/fridge")
        if response.status_code == 200:
            data = response.json()
            supplies_list = data.get('supplies', [])
            st.session_state.fridge_items = {
                item['name']: item['quantity'] 
                for item in supplies_list
            }
            st.session_state.fridge_order = [item['name'] for item in supplies_list]
    except requests.exceptions.ConnectionError:
        st.error(f"⚠️ Could not connect to backend at {API_URL}. Make sure the Spring Boot server is running.")


def update_fridge_order(ordered_items):
    """Sync new order to backend"""
    try:
        response = requests.put(f"{API_URL}/fridge/order", json={"items": ordered_items})
        if response.status_code == 200:
            st.session_state.fridge_order = ordered_items
            return True
    except requests.exceptions.ConnectionError:
        pass
    return False


def add_to_fridge(item, count=1):
    """Add item to fridge with count"""
    try:
        response = requests.post(f"{API_URL}/fridge/{item}", params={"count": count})
        if response.status_code == 200:
            fetch_fridge()
            if item in st.session_state.fridge_items:
                return "exists" if st.session_state.fridge_items[item] > count else "added"
            return "added"
    except requests.exceptions.ConnectionError:
        st.error("⚠️ Could not connect to backend.")
    return False


def remove_from_fridge(item):
    """Remove item from fridge"""
    try:
        response = requests.delete(f"{API_URL}/fridge/{item}")
        if response.status_code == 200:
            if item in st.session_state.fridge_items:
                del st.session_state.fridge_items[item]
            if item in st.session_state.pending_count_updates:
                del st.session_state.pending_count_updates[item]
            return True
    except requests.exceptions.ConnectionError:
        st.error("⚠️ Could not connect to backend.")
    return False


def update_item_count(item, new_count):
    """Update item count in session state and sync to backend immediately"""
    if item in st.session_state.fridge_items:
        st.session_state.fridge_items[item] = new_count
        try:
            response = requests.put(f"{API_URL}/fridge/{item}", json={"count": new_count})
            if response.status_code != 200:
                st.error(f"Failed to update count for {item}")
        except requests.exceptions.ConnectionError:
            st.error("⚠️ Could not connect to backend.")


def sync_pending_counts():
    """Sync pending count updates to backend (debounced)"""
    current_time = time.time()
    items_to_sync = []
    
    for item, (count, timestamp) in list(st.session_state.pending_count_updates.items()):
        if current_time - timestamp >= st.session_state.debounce_delay:
            items_to_sync.append((item, count))
    
    for item, count in items_to_sync:
        try:
            response = requests.put(f"{API_URL}/fridge/{item}", json={"count": count})
            if response.status_code == 200:
                del st.session_state.pending_count_updates[item]
        except requests.exceptions.ConnectionError:
            pass


def fetch_recipes_by_cuisine():
    """Fetch all recipes grouped by cuisine"""
    try:
        response = requests.get(f"{API_URL}/recipes")
        if response.status_code == 200:
            return response.json()
    except requests.exceptions.ConnectionError:
        pass
    return {}


def fetch_cuisines():
    """Fetch all cuisine types"""
    try:
        response = requests.get(f"{API_URL}/cuisines")
        if response.status_code == 200:
            return response.json()
    except requests.exceptions.ConnectionError:
        pass
    return []


def add_recipe(name, ingredients, cuisine_type, instructions, seasonings=None):
    """Add a new recipe"""
    try:
        data = {
            "name": name,
            "ingredients": ingredients,
            "cuisineType": cuisine_type,
            "instructions": instructions
        }
        if seasonings:
            data["seasonings"] = seasonings
        response = requests.post(f"{API_URL}/recipes", json=data)
        if response.status_code == 200:
            return True
    except requests.exceptions.ConnectionError:
        st.error("⚠️ Could not connect to backend.")
    return False


def delete_recipe(name):
    """Delete a recipe"""
    try:
        response = requests.delete(f"{API_URL}/recipes/{name}")
        if response.status_code == 200:
            return True
    except requests.exceptions.ConnectionError:
        st.error("⚠️ Could not connect to backend.")
    return False


def generate_cookable_recipes():
    """Generate list of cookable recipes from fridge contents"""
    try:
        response = requests.get(f"{API_URL}/generate")
        if response.status_code == 200:
            data = response.json()
            st.session_state.cookable_recipes = data.get('made', [])
            return True
    except requests.exceptions.ConnectionError:
        st.error("⚠️ Could not connect to backend.")
    return False


def fetch_recipe_details(recipe_name):
    """Fetch detailed recipe information"""
    try:
        response = requests.get(f"{API_URL}/recipes/{recipe_name}")
        if response.status_code == 200:
            return response.json()
    except requests.exceptions.ConnectionError:
        pass
    return None
