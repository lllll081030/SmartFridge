"""
SmartFridge - Main Application Entry Point

A Streamlit application that helps you discover what you can cook
with the ingredients in your fridge.
"""
import streamlit as st

# Import modules
from styles import apply_styles
from api import fetch_fridge, sync_pending_counts
from views import fridge, recipes, generate

# Page config
st.set_page_config(
    page_title="SmartFridge",
    page_icon="🍽️",
    layout="wide"
)

# Apply custom styles
apply_styles()

# Session state initialization
if 'fridge_items' not in st.session_state:
    st.session_state.fridge_items = {}
if 'cookable_recipes' not in st.session_state:
    st.session_state.cookable_recipes = []
if 'current_page' not in st.session_state:
    st.session_state.current_page = 'fridge'
if 'selected_cuisine' not in st.session_state:
    st.session_state.selected_cuisine = None
if 'pending_count_updates' not in st.session_state:
    st.session_state.pending_count_updates = {}
if 'debounce_delay' not in st.session_state:
    st.session_state.debounce_delay = 0.5
if 'fridge_order' not in st.session_state:
    st.session_state.fridge_order = []

# Main app header
st.markdown('<p class="main-header">🍽️ SmartFridge</p>', unsafe_allow_html=True)
st.markdown("*Find out what you can cook with what's in your fridge!*")

# Fetch current fridge contents on load and sync pending updates
fetch_fridge()
sync_pending_counts()

# ============== Sidebar Navigation ==============
st.sidebar.markdown("## Navigation")

if st.sidebar.button("🧊 My Fridge", use_container_width=True, type="primary" if st.session_state.current_page == 'fridge' else "secondary"):
    st.session_state.current_page = 'fridge'
    st.rerun()

if st.sidebar.button("📖 Recipe Book", use_container_width=True, type="primary" if st.session_state.current_page == 'recipes' else "secondary"):
    st.session_state.current_page = 'recipes'
    st.rerun()

if st.sidebar.button("🍳 Generate Recipes", use_container_width=True, type="primary" if st.session_state.current_page == 'generate' else "secondary"):
    st.session_state.current_page = 'generate'
    st.rerun()

# ============== Page Content ==============
if st.session_state.current_page == 'fridge':
    fridge.render()
elif st.session_state.current_page == 'recipes':
    recipes.render()
elif st.session_state.current_page == 'generate':
    generate.render()