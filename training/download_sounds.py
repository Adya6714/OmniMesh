import requests
import os
import time
import json
from pathlib import Path

# ─────────────────────────────────────────────────────────────────────────────
# Get your API key from freesound.org/apiv2/apply
# Takes 2 minutes, instant approval
API_KEY = "hS9spM3tZOjJ0eXvmwt9EBGjslS6L75dimC0FTYd"
BASE_URL = "https://freesound.org/apiv2"
OUTPUT_DIR = "audio_data"
TARGET_PER_CLASS = 100  # aim for 100 per class minimum

# ─────────────────────────────────────────────────────────────────────────────
# Search terms per class — multiple terms because Freesound's tagging is
# inconsistent. We search all terms and pool the results.
# Terms are ordered by expected relevance — best first.

SEARCH_CONFIG = {
    "distress": {
        "terms": [
            "screaming",
            "scream horror",
            "crying woman",
            "crying person",
            "person crying",
            "help screaming",
            "pain scream",
            "woman screaming",
            "child crying",
            "crying baby",
            "sobbing crying",
            "distress call",
            "yelling help",
            "moaning pain",
            "groaning",
        ],
        "target": 120,
        "duration_min": 2,
        "duration_max": 30,
    },
    "structural": {
        "terms": [
            "wood creak",
            "creaking wood",
            "building creak",
            "metal groan",
            "structure creak",
            "floor creak",
            "door creak",
            "concrete crack",
            "beam stress",
            "wood stress crack",
            "creaking door",
            "old building creak",
            "timber creak",
            "roof creak",
            "metal stress",
        ],
        "target": 100,
        "duration_min": 1,
        "duration_max": 20,
    },
    "fire": {
        "terms": [
            "fire crackling",
            "campfire crackling",
            "fireplace crackling",
            "fire burning",
            "wood fire",
            "bonfire",
            "fire indoor",
            "crackling fire",
            "burning fire",
        ],
        "target": 100,
        "duration_min": 3,
        "duration_max": 30,
    },
    "machinery": {
        "terms": [
            "construction machinery",
            "excavator",
            "bulldozer",
            "heavy machinery",
            "crane",
            "generator running",
            "diesel engine",
            "jackhammer",
            "power tools construction",
            "drill machine",
            "construction site",
            "chainsaw",
            "industrial machinery",
        ],
        "target": 100,
        "duration_min": 3,
        "duration_max": 30,
    },
    "ambient": {
        "terms": [
            "outdoor ambience",
            "city street ambient",
            "park ambient",
            "outdoor wind",
            "birds outdoor",
            "outdoor crowd",
            "street noise",
            "neighborhood ambient",
            "outdoor daytime",
            "wind outdoor",
        ],
        "target": 100,
        "duration_min": 5,
        "duration_max": 30,
    },
    "silence": {
        "terms": [
            "room tone",
            "indoor silence",
            "room ambience quiet",
            "interior room tone",
            "quiet room",
            "indoor room tone",
            "basement ambience",
            "corridor ambience quiet",
            "small room ambience",
        ],
        "target": 60,
        # silence clips tend to be short
        "duration_min": 2,
        "duration_max": 30,
    },
}

# ─────────────────────────────────────────────────────────────────────────────

def search_freesound(query, duration_min, duration_max, page=1):
    """Search Freesound and return list of sound objects."""
    url = f"{BASE_URL}/search/text/"
    params = {
        "query": query,
        "fields": "id,name,previews,duration,tags,license",
        "filter": (
            f"duration:[{duration_min} TO {duration_max}] "
            f"license:(\"Creative Commons 0\" OR "
            f"\"Attribution\" OR \"Attribution NonCommercial\")"
        ),
        "page_size": 50,
        "page": page,
        "token": API_KEY,
    }
    try:
        resp = requests.get(url, params=params, timeout=15)
        if resp.status_code == 200:
            return resp.json()
        elif resp.status_code == 429:
            print("  Rate limited — waiting 60 seconds...")
            time.sleep(60)
            return search_freesound(query, duration_min, duration_max, page)
        else:
            print(f"  Search error {resp.status_code}: {query}")
            return {"results": [], "count": 0}
    except requests.exceptions.Timeout:
        print(f"  Timeout on search: {query}")
        return {"results": [], "count": 0}

def download_preview(sound_id, preview_url, output_path):
    """Download the HQ MP3 preview of a sound."""
    try:
        resp = requests.get(preview_url, timeout=30)
        if resp.status_code == 200:
            with open(output_path, 'wb') as f:
                f.write(resp.content)
            return True
    except Exception as e:
        print(f"  Download error {sound_id}: {e}")
    return False

def get_existing_ids(class_dir):
    """Get set of already-downloaded sound IDs to avoid re-downloading."""
    existing = set()
    if os.path.exists(class_dir):
        for f in os.listdir(class_dir):
            # Files are named {sound_id}.mp3
            if f.endswith('.mp3'):
                try:
                    existing.add(int(f.replace('.mp3', '')))
                except ValueError:
                    pass
    return existing

def collect_class(class_name, config):
    """Download sounds for one class until target is reached."""
    class_dir = os.path.join(OUTPUT_DIR, class_name)
    os.makedirs(class_dir, exist_ok=True)
    
    existing_ids = get_existing_ids(class_dir)
    current_count = len(existing_ids)
    target = config["target"]
    
    print(f"\n{'='*60}")
    print(f"CLASS: {class_name.upper()}")
    print(f"Have: {current_count} | Target: {target}")
    
    if current_count >= target:
        print(f"  Already at target — skipping")
        return current_count
    
    needed = target - current_count
    downloaded = 0
    seen_ids = set(existing_ids)
    
    for term in config["terms"]:
        if downloaded >= needed:
            break
            
        print(f"\n  Searching: '{term}'")
        page = 1
        
        while downloaded < needed:
            results = search_freesound(
                term, 
                config["duration_min"],
                config["duration_max"],
                page
            )
            
            sounds = results.get("results", [])
            if not sounds:
                break
            
            for sound in sounds:
                if downloaded >= needed:
                    break
                    
                sound_id = sound["id"]
                if sound_id in seen_ids:
                    continue
                    
                seen_ids.add(sound_id)
                
                preview_url = sound.get("previews", {}).get(
                    "preview-hq-mp3", ""
                )
                if not preview_url:
                    continue
                
                output_path = os.path.join(class_dir, f"{sound_id}.mp3")
                
                success = download_preview(
                    sound_id, preview_url, output_path
                )
                if success:
                    downloaded += 1
                    total = current_count + downloaded
                    print(
                        f"  [{total}/{target}] {sound['name'][:50]}"
                        f" ({sound['duration']:.1f}s)"
                    )
                
                # Be respectful to Freesound API
                time.sleep(0.4)
            
            # Check if there are more pages
            total_results = results.get("count", 0)
            if page * 50 >= total_results:
                break
            page += 1
            time.sleep(1)
    
    final_count = current_count + downloaded
    print(f"\n  {class_name}: {downloaded} new downloads → {final_count} total")
    return final_count

def print_summary():
    """Print final count per class."""
    print(f"\n{'='*60}")
    print("FINAL DATASET SUMMARY")
    print('='*60)
    total = 0
    for class_name in SEARCH_CONFIG:
        class_dir = os.path.join(OUTPUT_DIR, class_name)
        if os.path.exists(class_dir):
            count = len([f for f in os.listdir(class_dir) 
                        if f.endswith('.mp3')])
        else:
            count = 0
        status = "✓" if count >= SEARCH_CONFIG[class_name]["target"] * 0.7 else "✗"
        print(f"  {status} {class_name:12} {count:4} files "
              f"(target: {SEARCH_CONFIG[class_name]['target']})")
        total += count
    print(f"\n  Total: {total} files")
    print('='*60)

# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    if API_KEY == "YOUR_FREESOUND_API_KEY":
        print("ERROR: Set your API key at the top of this script")
        print("Get one at: freesound.org/apiv2/apply")
        exit(1)
    
    print("OmniMesh Audio Dataset Collector")
    print(f"Target: {TARGET_PER_CLASS} clips per class")
    print(f"Output: {os.path.abspath(OUTPUT_DIR)}/")
    
    # Process distress and structural first — most important and most missing
    priority_order = [
        "distress",
        "structural", 
        "ambient",
        "fire",
        "machinery",
        "silence",
    ]
    
    results = {}
    for class_name in priority_order:
        config = SEARCH_CONFIG[class_name]
        count = collect_class(class_name, config)
        results[class_name] = count
        # Pause between classes to avoid rate limiting
        time.sleep(2)
    
    print_summary()
    
    print("\nNEXT STEPS:")
    print("1. Record 15-20 'silence' clips yourself in an enclosed space")
    print("   (bathroom/closet, door closed, 10-15 seconds each)")
    print("   Save as audio_data/silence/recorded_XX.mp3")
    print("2. Zip the folder: zip -r audio_data.zip audio_data/")
    print("3. Upload audio_data.zip to Google Drive")
    print("4. Open the Colab notebook and run all cells")