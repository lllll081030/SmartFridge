import streamlit as st

def apply_styles():
    """Apply custom CSS styles to the app"""
    st.markdown("""
    <style>
        .main-header {
            font-size: 2.5rem;
            font-weight: bold;
            color: #1E88E5;
            margin-bottom: 0.5rem;
        }
        .section-header {
            font-size: 1.5rem;
            font-weight: bold;
            color: #424242;
            margin-top: 1rem;
            margin-bottom: 0.5rem;
        }
        .cuisine-tag {
            background-color: #E3F2FD;
            padding: 0.25rem 0.5rem;
            border-radius: 0.25rem;
            font-size: 0.875rem;
            color: #1565C0;
        }
        .ingredient-chip {
            background-color: #E8F5E9;
            padding: 0.2rem 0.5rem;
            border-radius: 1rem;
            font-size: 0.8rem;
            margin-right: 0.25rem;
            display: inline-block;
        }
        .cookable-recipe {
            background-color: #C8E6C9;
            padding: 0.5rem 1rem;
            border-radius: 0.5rem;
            margin: 0.25rem 0;
        }
        .fridge-item {
            background-color: #f5f5f5;
            padding: 0.5rem 1rem;
            border-radius: 0.5rem;
            margin: 0.25rem 0;
            border: 1px solid #e0e0e0;
        }
    </style>
    """, unsafe_allow_html=True)
