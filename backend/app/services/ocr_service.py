import cv2
import numpy as np
import pytesseract
from typing import List, Dict, Tuple

def preprocess_image(image_path: str) -> np.ndarray:
    """Read image, convert to grayscale, and apply thresholding."""
    img = cv2.imread(image_path)
    if img is None:
        raise ValueError(f"Could not read image: {image_path}")
        
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # Adaptive thresholding to handle different lighting
    thresh = cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, 
                                   cv2.THRESH_BINARY_INV, 11, 2)
    return img, gray, thresh

def extract_table_grid(thresh: np.ndarray) -> Tuple[List, List]:
    """Extract horizontal and vertical lines to find table grid."""
    # Find horizontal lines
    horizontal_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (40, 1))
    detect_horizontal = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, horizontal_kernel, iterations=2)
    cnts_h = cv2.findContours(detect_horizontal, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    cnts_h = cnts_h[0] if len(cnts_h) == 2 else cnts_h[1]
    
    # Find vertical lines
    vertical_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (1, 40))
    detect_vertical = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, vertical_kernel, iterations=2)
    cnts_v = cv2.findContours(detect_vertical, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    cnts_v = cnts_v[0] if len(cnts_v) == 2 else cnts_v[1]
    
    return cnts_h, cnts_v

def extract_cells_from_grid(img: np.ndarray, thresh: np.ndarray) -> List[Dict]:
    """Finds cell bounding boxes based on the intersection of lines."""
    # Combine horizontal and vertical lines to get grid
    horizontal_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (40, 1))
    detect_horizontal = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, horizontal_kernel, iterations=2)
    
    vertical_kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (1, 40))
    detect_vertical = cv2.morphologyEx(thresh, cv2.MORPH_OPEN, vertical_kernel, iterations=2)
    
    # Combine
    table_mask = cv2.addWeighted(detect_vertical, 0.5, detect_horizontal, 0.5, 0.0)
    table_mask = cv2.threshold(table_mask, 50, 255, cv2.THRESH_BINARY | cv2.THRESH_OTSU)[1]
    
    # Find contours (cells)
    cnts = cv2.findContours(table_mask, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)
    cnts = cnts[0] if len(cnts) == 2 else cnts[1]
    
    cells = []
    for c in cnts:
        x, y, w, h = cv2.boundingRect(c)
        # Filter out very small boxes (noise) or very large boxes (whole table)
        if w > 50 and h > 20 and w < img.shape[1] * 0.9:
            cell_roi = img[y:y+h, x:x+w]
            cells.append({
                "x": x, "y": y, "w": w, "h": h,
                "roi": cell_roi
            })
            
    # Sort cells top-to-bottom, then left-to-right
    cells.sort(key=lambda b: (b['y'] // 10, b['x']))
    return cells

def perform_ocr_on_cell(roi: np.ndarray) -> str:
    """Perform OCR on a given region of interest."""
    # Preprocess ROI for better OCR
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    gray = cv2.resize(gray, None, fx=2, fy=2, interpolation=cv2.INTER_CUBIC)
    
    # Simple thresholding
    _, binary = cv2.threshold(gray, 150, 255, cv2.THRESH_BINARY)
    
    # Tesseract configuration (assume English, PSM 6 assumes a single uniform block of text)
    custom_config = r'--oem 3 --psm 6'
    text = pytesseract.image_to_string(binary, config=custom_config)
    
    return text.strip()

def process_timetable_image(image_path: str) -> List[Dict]:
    """Main pipeline for image -> structured cells."""
    img, gray, thresh = preprocess_image(image_path)
    cells = extract_cells_from_grid(img, thresh)
    
    extracted_data = []
    for cell in cells:
        text = perform_ocr_on_cell(cell['roi'])
        if text: # Ignore empty cells
            extracted_data.append({
                "x": cell['x'],
                "y": cell['y'],
                "w": cell['w'],
                "h": cell['h'],
                "text": text
            })
            
    return extracted_data
