import numpy as np
import shapely.ops
import tifffile

from pyproj import Transformer
from shapely.geometry.point import Point
from shapely.geometry.polygon import Polygon
from typing import List


class RasterCreator:
    points: List[List[float]] = []

    def __init__(self, points, output_raster_path):
        self.points = points
        self.output_raster_path = output_raster_path

    def create_raster(self):
        altitudes = np.array([p[2] for p in self.points])

        points_4326 = [Point(p[1], p[0]) for p in self.points]
        transformer = Transformer.from_crs(4326, 3857, always_xy=True)
        polygon_4326 = Polygon(points_4326)
        bounding_box = polygon_4326.bounds

        bounding_box_polygon_4326 = Polygon.from_bounds(*bounding_box)
        bounding_box_polygon = shapely.ops.transform(transformer.transform, bounding_box_polygon_4326)
        points = [shapely.ops.transform(transformer.transform, p) for p in points_4326]

        x_min, y_min, x_max, y_max = bounding_box_polygon.bounds
        power = 2

        width, height = 512, 512

        def coords_to_pixel(x, y):
            x_ = (x - x_min) / (x_max - x_min) * (width - 1)
            y_ = (y_max - y) / (y_max - y_min) * (height - 1)
            return x_, y_

        points_px = np.array([coords_to_pixel(p.x, p.y) for p in points])
        grid = np.zeros((height, width), dtype=np.float32)

        for y in range(height):
            for x in range(width):
                dx = points_px[:, 0] - x
                dy = points_px[:, 1] - y
                dist = np.sqrt(dx ** 2 + dy ** 2)

                dist[dist == 0] = 1e-6

                weights = 1 / (dist ** power)
                grid[y, x] = np.sum(weights * altitudes) / np.sum(weights)

        pixel_size_x = (x_max - x_min) / (width - 1)
        pixel_size_y = (y_max - y_min) / (height - 1)

        scale = (pixel_size_x, pixel_size_y, 0.0)
        tiepoint = (0.0, 0.0, 0.0, x_min, y_max, 0.0)
        geo_keys = [
            1, 1, 0, 3,
            1024, 0, 1, 1,
            2048, 0, 1, 3857
        ]

        with tifffile.TiffWriter(self.output_raster_path) as tiff:
            tiff.write(
                grid.astype(np.float32),
                metadata=None,
                extratags=[
                    (33550, "d", 3, scale, False),
                    (33922, "d", 6, tiepoint, False),
                    (34735, "H", len(geo_keys), geo_keys, False),
                ]
            )