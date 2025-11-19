import os
from PIL import Image, ImageDraw

def generate_icon(size, output_path, is_round=False):
    # Create a new image with a transparent background
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    # Draw the background (Blue square or circle)
    bg_color = (33, 150, 243, 255) # #2196F3
    
    if is_round:
        draw.ellipse((0, 0, size, size), fill=bg_color)
    else:
        # Rounded rectangle for square icon
        # radius = size // 8
        # draw.rounded_rectangle((0, 0, size, size), radius=radius, fill=bg_color)
        draw.rectangle((0, 0, size, size), fill=bg_color)

    # House polygon points (based on 24x24 grid)
    # M10,20 v-6 h4 v6 h5 v-8 h3 L12,3 2,12 h3 v8 z
    # Points: (10, 20), (10, 14), (14, 14), (14, 20), (19, 20), (19, 12), (22, 12), (12, 3), (2, 12), (5, 12), (5, 20)
    
    points_24 = [
        (10, 20), (10, 14), (14, 14), (14, 20), (19, 20), 
        (19, 12), (22, 12), (12, 3), (2, 12), (5, 12), (5, 20)
    ]

    # Scale points to icon size with padding
    padding = size * 0.25
    scale = (size - 2 * padding) / 24
    
    scaled_points = []
    for x, y in points_24:
        sx = x * scale + padding
        sy = y * scale + padding
        scaled_points.append((sx, sy))

    # Draw the house
    draw.polygon(scaled_points, fill="white")

    # Ensure directory exists
    if os.path.dirname(output_path):
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
    
    # Save the image
    image.save(output_path, "PNG")
    print(f"Generated {output_path}")

def main():
    base_dir = "app/src/main/res"
    
    # Standard Android Icon Sizes
    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192
    }

    for folder, size in sizes.items():
        # Generate square icon
        generate_icon(size, os.path.join(base_dir, folder, "ic_launcher.png"), is_round=False)
        # Generate round icon
        generate_icon(size, os.path.join(base_dir, folder, "ic_launcher_round.png"), is_round=True)

    # Generate Play Store Icon
    generate_icon(512, "App_Icon.png", is_round=False)
    
    # Generate Feature Graphic (1024x500)
    generate_feature_graphic()

def generate_feature_graphic():
    width = 1024
    height = 500
    image = Image.new("RGBA", (width, height), (33, 150, 243, 255)) # Blue background
    draw = ImageDraw.Draw(image)
    
    # Draw a large house in the center
    icon_size = 300
    
    points_24 = [
        (10, 20), (10, 14), (14, 14), (14, 20), (19, 20), 
        (19, 12), (22, 12), (12, 3), (2, 12), (5, 12), (5, 20)
    ]
    
    # Center the house
    scale = icon_size / 24
    offset_x = (width - icon_size) / 2
    offset_y = (height - icon_size) / 2
    
    scaled_points = []
    for x, y in points_24:
        sx = x * scale + offset_x
        sy = y * scale + offset_y
        scaled_points.append((sx, sy))
        
    draw.polygon(scaled_points, fill="white")
    
    image.save("feature_graphic_1024x500.png", "PNG")
    print("Generated feature_graphic_1024x500.png")

if __name__ == "__main__":
    main()
