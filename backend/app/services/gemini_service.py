from google import genai
from google.genai import types
from app.core.config import settings

def generate_schedule_explanation(student_data: dict, current_class: dict, next_class: dict):
    if not settings.GEMINI_API_KEY:
        return None
        
    client = genai.Client(api_key=settings.GEMINI_API_KEY)
    
    prompt = f"""
You are an AI assistant for a university timetable system.
Based on the following data, write a natural language explanation (1-2 sentences) of the student's current and next class.
Do not invent any information. Only use the provided data.
Data:
Student Program: {student_data.get('program')}
Section: {student_data.get('section')}
Current Class: {current_class}
Next Class: {next_class}
"""
    
    try:
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=prompt,
        )
        return response.text
    except Exception as e:
        print(f"Gemini API Error: {e}")
        return None
