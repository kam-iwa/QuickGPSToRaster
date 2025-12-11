import numpy as np
import shapely.ops

from PIL import Image
from pyproj import Transformer
from shapely.geometry.point import Point
from shapely.geometry.polygon import Polygon
from typing import List


class RasterCreator:
    points: List[List[float]] = []
    crs: str = """PROJCRS["WGS 84 / Pseudo-Mercator",BASEGEOGCRS["WGS 84",ENSEMBLE["World Geodetic System 1984 ensemble",MEMBER["World Geodetic System 1984 (Transit)"],MEMBER["World Geodetic System 1984 (G730)"],MEMBER["World Geodetic System 1984 (G873)"],MEMBER["World Geodetic System 1984 (G1150)"],MEMBER["World Geodetic System 1984 (G1674)"],MEMBER["World Geodetic System 1984 (G1762)"],MEMBER["World Geodetic System 1984 (G2139)"],MEMBER["World Geodetic System 1984 (G2296)"],ELLIPSOID["WGS 84",6378137,298.257223563,LENGTHUNIT["metre",1]],ENSEMBLEACCURACY[2.0]],PRIMEM["Greenwich",0,ANGLEUNIT["degree",0.0174532925199433]],ID["EPSG",4326]],CONVERSION["Popular Visualisation Pseudo-Mercator",METHOD["Popular Visualisation Pseudo Mercator",ID["EPSG",1024]],PARAMETER["Latitude of natural origin",0,ANGLEUNIT["degree",0.0174532925199433],ID["EPSG",8801]],PARAMETER["Longitude of natural origin",0,ANGLEUNIT["degree",0.0174532925199433],ID["EPSG",8802]],PARAMETER["False easting",0,LENGTHUNIT["metre",1],ID["EPSG",8806]],PARAMETER["False northing",0,LENGTHUNIT["metre",1],ID["EPSG",8807]]],CS[Cartesian,2],AXIS["easting (X)",east,ORDER[1],LENGTHUNIT["metre",1]],AXIS["northing (Y)",north,ORDER[2],LENGTHUNIT["metre",1]],USAGE[SCOPE["Web mapping and visualisation."],AREA["World between 85.06�S and 85.06�N."],BBOX[-85.06,-180,85.06,180]],ID["EPSG",3857]]"""

    def __init__(self, points, output_raster_path, output_control_points_path):
        self.points = points
        self.output_raster_path = output_raster_path
        self.output_control_points_path = output_control_points_path

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

        grid_norm = (grid - grid.min()) / (grid.max() - grid.min())
        grid_16bit = (grid_norm * 65535).astype(np.uint16)

        image = Image.fromarray(grid_16bit)
        image.save(self.output_raster_path)

        with open(self.output_control_points_path, "w", encoding='utf-8') as f:
            f.write(f"#CRS: {self.crs}\nmapX,mapY,sourceX,sourceY,enable,dX,dY,residual\n")
            f.write(f"{x_min},{y_max},0.00,0.00,1,0,0,0\n")
            f.write(f"{x_max},{y_min},511.00,-511.00,1,0,0,0\n")